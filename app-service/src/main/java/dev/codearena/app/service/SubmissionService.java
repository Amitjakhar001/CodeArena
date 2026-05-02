package dev.codearena.app.service;

import dev.codearena.app.domain.Problem;
import dev.codearena.app.domain.Submission;
import dev.codearena.app.domain.SubmissionStatus;
import dev.codearena.app.dto.CreateSubmissionRequest;
import dev.codearena.app.dto.ExecutionResultDto;
import dev.codearena.app.exception.ProblemNotFoundException;
import dev.codearena.app.exception.SubmissionForbiddenException;
import dev.codearena.app.exception.SubmissionNotFoundException;
import dev.codearena.app.exception.UnsupportedLanguageException;
import dev.codearena.app.grpc.ExecutionServiceClient;
import dev.codearena.app.repository.SubmissionRepository;
import dev.codearena.proto.execution.v1.ExecutionResult;
import dev.codearena.proto.execution.v1.SubmitCodeRequest;
import dev.codearena.proto.execution.v1.SubmitCodeResponse;
import org.bson.types.ObjectId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Service
public class SubmissionService {

    private static final Logger log = LoggerFactory.getLogger(SubmissionService.class);

    private final SubmissionRepository repository;
    private final ProblemService problemService;
    private final ExecutionServiceClient executionClient;
    private final UserStatsService userStatsService;

    public SubmissionService(SubmissionRepository repository,
                             ProblemService problemService,
                             ExecutionServiceClient executionClient,
                             UserStatsService userStatsService) {
        this.repository = repository;
        this.problemService = problemService;
        this.executionClient = executionClient;
        this.userStatsService = userStatsService;
    }

    public Submission create(ObjectId userId, CreateSubmissionRequest req) {
        Problem problem = null;
        if (req.problemId() != null && !req.problemId().isBlank()) {
            ObjectId pid;
            try {
                pid = new ObjectId(req.problemId());
            } catch (IllegalArgumentException e) {
                throw new ProblemNotFoundException("Invalid problemId: " + req.problemId());
            }
            problem = problemService.findById(pid)
                .orElseThrow(() -> new ProblemNotFoundException("No problem with id: " + req.problemId()));
            if (!problem.getSupportedLanguages().contains(req.languageId())) {
                throw new UnsupportedLanguageException(
                    "Language " + req.languageId() + " not supported for problem " + problem.getSlug());
            }
        }

        Instant now = Instant.now();

        Submission submission = new Submission();
        submission.setId(new ObjectId());
        submission.setUserId(userId);
        submission.setProblemId(problem == null ? null : problem.getId());
        submission.setLanguageId(req.languageId());
        submission.setSourceCode(req.sourceCode());
        submission.setCustomStdin(req.customStdin());
        submission.setLatestStatus(SubmissionStatus.PENDING);
        submission.setCreatedAt(now);
        submission.setUpdatedAt(now);

        // Persist before calling Execution so the submission row exists if Execution flakes.
        repository.save(submission);

        SubmitCodeRequest.Builder reqBuilder = SubmitCodeRequest.newBuilder()
            .setUserId(userId.toHexString())
            .setSubmissionId(submission.getId().toHexString())
            .setLanguageId(req.languageId())
            .setSourceCode(req.sourceCode());
        if (req.customStdin() != null) reqBuilder.setStdin(req.customStdin());

        // Use the first non-hidden sample test case's expected output as the comparator
        // so the playground / sample run can produce ACCEPTED vs WRONG_ANSWER.
        if (problem != null && problem.getSampleTestCases() != null) {
            problem.getSampleTestCases().stream()
                .filter(tc -> !tc.isHidden())
                .findFirst()
                .ifPresent(tc -> {
                    if (tc.getInput() != null) reqBuilder.setStdin(tc.getInput());
                    if (tc.getExpectedOutput() != null) reqBuilder.setExpectedOutput(tc.getExpectedOutput());
                });
        }

        SubmitCodeResponse resp = executionClient.submitCode(reqBuilder.build());

        submission.getExecutionTokens().add(resp.getExecutionToken());
        submission.setLatestStatus(resp.getStatus());  // "QUEUED" from Execution
        submission.setUpdatedAt(Instant.now());
        repository.save(submission);

        log.info("Created submission {} for user {} (token={})",
            submission.getId(), userId, resp.getExecutionToken());

        return submission;
    }

    /**
     * Loads the submission and refreshes its latest execution from the Execution Service.
     * If the latest execution has just transitioned to a terminal status, denormalizes the
     * outcome into user_stats.
     */
    public ResolvedSubmission getAndRefresh(ObjectId userId, ObjectId submissionId) {
        Submission submission = repository.findById(submissionId)
            .orElseThrow(() -> new SubmissionNotFoundException("No submission with id: " + submissionId));

        if (!submission.getUserId().equals(userId)) {
            throw new SubmissionForbiddenException("Submission does not belong to requesting user");
        }

        if (submission.getExecutionTokens().isEmpty()) {
            return new ResolvedSubmission(submission, List.of());
        }

        List<ExecutionResult> results;
        if (submission.getExecutionTokens().size() == 1) {
            String token = submission.getExecutionTokens().get(0);
            Optional<ExecutionResult> single = executionClient.getExecutionResult(token, userId.toHexString());
            results = single.map(List::of).orElseGet(List::of);
        } else {
            results = executionClient.getExecutionResultsBatch(
                submission.getExecutionTokens(), userId.toHexString());
        }

        if (!results.isEmpty()) {
            ExecutionResult latest = results.get(results.size() - 1);
            applyLatest(submission, latest);
        }

        List<ExecutionResultDto> dtos = results.stream().map(ExecutionResultDto::from).toList();
        return new ResolvedSubmission(submission, dtos);
    }

    private void applyLatest(Submission submission, ExecutionResult latest) {
        String previousStatus = submission.getLatestStatus();
        String newStatus = latest.getStatus();
        boolean changed = !newStatus.equals(previousStatus);
        boolean newlyTerminal = SubmissionStatus.isTerminal(newStatus)
            && !SubmissionStatus.isTerminal(previousStatus);

        if (!changed) return;

        submission.setLatestStatus(newStatus);
        submission.setUpdatedAt(Instant.now());
        repository.save(submission);

        if (newlyTerminal) {
            userStatsService.recordTerminal(
                submission.getUserId(),
                submission.getLanguageId(),
                newStatus,
                latest.getTimeMs());
        }
    }

    public Page<Submission> listForUser(ObjectId userId, ObjectId problemId, Pageable pageable) {
        if (problemId == null) {
            return repository.findByUserId(userId, pageable);
        }
        return repository.findByUserIdAndProblemId(userId, problemId, pageable);
    }

    public record ResolvedSubmission(Submission submission, List<ExecutionResultDto> executions) {}
}
