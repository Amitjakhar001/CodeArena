package dev.codearena.execution.service;

import dev.codearena.execution.config.Judge0Properties;
import dev.codearena.execution.domain.Execution;
import dev.codearena.execution.domain.ExecutionStatus;
import dev.codearena.execution.dto.Judge0Submission;
import dev.codearena.execution.dto.Judge0SubmissionRequest;
import dev.codearena.execution.dto.Judge0SubmissionToken;
import dev.codearena.execution.exception.ExecutionForbiddenException;
import dev.codearena.execution.exception.ExecutionNotFoundException;
import dev.codearena.execution.exception.UnsupportedLanguageException;
import dev.codearena.execution.repository.ExecutionLanguageRepository;
import dev.codearena.execution.repository.ExecutionRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

@Service
public class ExecutionService {

    private static final Logger log = LoggerFactory.getLogger(ExecutionService.class);
    private static final BigDecimal DEFAULT_CPU = new BigDecimal("5.00");
    private static final int DEFAULT_MEMORY_KB = 128_000;

    private final ExecutionRepository executions;
    private final ExecutionLanguageRepository languages;
    private final Judge0Client judge0;
    private final Judge0Properties props;

    public ExecutionService(ExecutionRepository executions,
                            ExecutionLanguageRepository languages,
                            Judge0Client judge0,
                            Judge0Properties props) {
        this.executions = executions;
        this.languages = languages;
        this.judge0 = judge0;
        this.props = props;
    }

    @Transactional
    public Execution submit(String userId,
                            String submissionId,
                            int languageId,
                            String sourceCode,
                            String stdin,
                            String expectedOutput,
                            Double cpuTimeLimitSeconds,
                            Integer memoryLimitKb) {

        if (!languages.existsById(languageId)) {
            throw new UnsupportedLanguageException(languageId);
        }

        BigDecimal cpu = cpuTimeLimitSeconds != null && cpuTimeLimitSeconds > 0
                ? BigDecimal.valueOf(cpuTimeLimitSeconds)
                : DEFAULT_CPU;
        int mem = memoryLimitKb != null && memoryLimitKb > 0
                ? memoryLimitKb
                : DEFAULT_MEMORY_KB;

        Judge0SubmissionRequest req = new Judge0SubmissionRequest(
                sourceCode,
                languageId,
                emptyToNull(stdin),
                emptyToNull(expectedOutput),
                cpu.doubleValue(),
                mem,
                buildCallbackUrl()
        );
        Judge0SubmissionToken token = judge0.submitAsync(req);
        if (token == null || token.token() == null || token.token().isBlank()) {
            throw new IllegalStateException("Judge0 returned no token");
        }

        Execution e = new Execution();
        e.setJudge0Token(token.token());
        e.setUserId(userId);
        e.setSubmissionId(emptyToNull(submissionId));
        e.setLanguageId(languageId);
        e.setSourceCodeHash(SourceCodeHasher.sha256Hex(sourceCode));
        e.setStdin(emptyToNull(stdin));
        e.setExpectedOutput(emptyToNull(expectedOutput));
        e.setCpuTimeLimit(cpu);
        e.setMemoryLimit(mem);
        e.setStatusDescription(ExecutionStatus.QUEUED);
        e.setCreatedAt(OffsetDateTime.now());
        return executions.save(e);
    }

    @Transactional(readOnly = true)
    public Optional<Execution> findOwned(String token, String requestingUserId) {
        Optional<Execution> opt = executions.findByJudge0Token(token);
        opt.ifPresent(e -> requireOwner(e, requestingUserId));
        return opt;
    }

    @Transactional(readOnly = true)
    public List<Execution> findOwnedBatch(List<String> tokens, String requestingUserId) {
        if (tokens.isEmpty()) return List.of();
        List<Execution> rows = executions.findByJudge0TokenIn(tokens);
        rows.forEach(e -> requireOwner(e, requestingUserId));
        return rows;
    }

    /**
     * Apply a Judge0 submission payload (from webhook or recovery poll) to our row.
     * Idempotent — once an execution is terminal, subsequent payloads are ignored.
     */
    @Transactional
    public void applyJudge0Result(String token, Judge0Submission payload) {
        Execution e = executions.findByJudge0Token(token)
                .orElseThrow(() -> new ExecutionNotFoundException(token));

        if (e.getCompletedAt() != null) {
            log.debug("Ignoring Judge0 callback for already-completed token {}", token);
            return;
        }

        Integer judge0StatusId = payload.status() == null ? null : payload.status().id();
        e.setStatusId(judge0StatusId);
        e.setStatusDescription(Judge0StatusMapper.map(judge0StatusId));
        e.setStdout(payload.stdout());
        e.setStderr(payload.stderr());
        e.setCompileOutput(payload.compileOutput());
        e.setTimeMs(parseTimeMs(payload.time()));
        e.setMemoryKb(payload.memory());
        if (Judge0StatusMapper.isTerminal(judge0StatusId)) {
            e.setCompletedAt(OffsetDateTime.now());
        }
        executions.save(e);
    }

    /**
     * Find executions still PENDING past the recovery threshold and ask Judge0
     * directly. Webhooks are happy path; this is the safety net.
     */
    @Transactional
    public int recoverStuckExecutions() {
        OffsetDateTime cutoff = OffsetDateTime.now().minusSeconds(props.recoveryStuckSeconds());
        List<Execution> stuck = executions.findStuckPending(cutoff);
        int recovered = 0;
        for (Execution e : stuck) {
            try {
                Optional<Judge0Submission> sub = judge0.getSubmission(e.getJudge0Token());
                if (sub.isEmpty()) continue;
                Integer statusId = sub.get().status() == null ? null : sub.get().status().id();
                if (Judge0StatusMapper.isTerminal(statusId)) {
                    applyJudge0Result(e.getJudge0Token(), sub.get());
                    recovered++;
                }
            } catch (RuntimeException ex) {
                log.warn("Recovery poll failed for token {}: {}", e.getJudge0Token(), ex.getMessage());
            }
        }
        if (recovered > 0) {
            log.info("Recovery poller: {} stuck executions recovered (of {} polled)", recovered, stuck.size());
        }
        return recovered;
    }

    private void requireOwner(Execution e, String requestingUserId) {
        if (requestingUserId != null && !requestingUserId.isBlank()
                && !requestingUserId.equals(e.getUserId())) {
            throw new ExecutionForbiddenException();
        }
    }

    private String buildCallbackUrl() {
        String base = props.webhookBaseUrl();
        if (base == null || base.isBlank()) return null;
        return base.endsWith("/") ? base + "webhook/judge0" : base + "/webhook/judge0";
    }

    private static String emptyToNull(String s) {
        return (s == null || s.isEmpty()) ? null : s;
    }

    private static Integer parseTimeMs(String time) {
        if (time == null || time.isBlank()) return null;
        try {
            return (int) Math.round(Double.parseDouble(time) * 1000.0);
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
