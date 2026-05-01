package dev.codearena.execution.grpc;

import dev.codearena.execution.domain.Execution;
import dev.codearena.execution.domain.ExecutionLanguage;
import dev.codearena.execution.domain.ExecutionStatus;
import dev.codearena.execution.repository.ExecutionLanguageRepository;
import dev.codearena.execution.service.ExecutionService;
import dev.codearena.proto.common.v1.Empty;
import dev.codearena.proto.execution.v1.ExecutionResult;
import dev.codearena.proto.execution.v1.ExecutionServiceGrpc;
import dev.codearena.proto.execution.v1.GetExecutionResultRequest;
import dev.codearena.proto.execution.v1.GetExecutionResultResponse;
import dev.codearena.proto.execution.v1.GetExecutionResultsBatchRequest;
import dev.codearena.proto.execution.v1.GetExecutionResultsBatchResponse;
import dev.codearena.proto.execution.v1.Language;
import dev.codearena.proto.execution.v1.ListLanguagesResponse;
import dev.codearena.proto.execution.v1.SubmitCodeRequest;
import dev.codearena.proto.execution.v1.SubmitCodeResponse;
import io.grpc.Status;
import io.grpc.stub.StreamObserver;
import net.devh.boot.grpc.server.service.GrpcService;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

@GrpcService
public class ExecutionGrpcService extends ExecutionServiceGrpc.ExecutionServiceImplBase {

    private final ExecutionService executions;
    private final ExecutionLanguageRepository languages;

    public ExecutionGrpcService(ExecutionService executions,
                                ExecutionLanguageRepository languages) {
        this.executions = executions;
        this.languages = languages;
    }

    @Override
    public void submitCode(SubmitCodeRequest req, StreamObserver<SubmitCodeResponse> resp) {
        try {
            Execution e = executions.submit(
                    req.getUserId(),
                    req.getSubmissionId(),
                    req.getLanguageId(),
                    req.getSourceCode(),
                    req.getStdin(),
                    req.getExpectedOutput(),
                    req.getCpuTimeLimitSeconds(),
                    req.getMemoryLimitKb()
            );
            resp.onNext(SubmitCodeResponse.newBuilder()
                    .setExecutionToken(e.getJudge0Token())
                    .setStatus(ExecutionStatus.QUEUED)
                    .build());
            resp.onCompleted();
        } catch (RuntimeException ex) {
            resp.onError(Status.INTERNAL.withDescription(ex.getMessage()).withCause(ex).asRuntimeException());
        }
    }

    @Override
    public void getExecutionResult(GetExecutionResultRequest req, StreamObserver<GetExecutionResultResponse> resp) {
        try {
            Optional<Execution> opt = executions.findOwned(req.getExecutionToken(), req.getRequestingUserId());
            GetExecutionResultResponse.Builder out = GetExecutionResultResponse.newBuilder();
            if (opt.isPresent()) {
                out.setFound(true).setResult(toProto(opt.get()));
            } else {
                out.setFound(false);
            }
            resp.onNext(out.build());
            resp.onCompleted();
        } catch (RuntimeException ex) {
            resp.onError(Status.INTERNAL.withDescription(ex.getMessage()).withCause(ex).asRuntimeException());
        }
    }

    @Override
    public void getExecutionResultsBatch(GetExecutionResultsBatchRequest req,
                                         StreamObserver<GetExecutionResultsBatchResponse> resp) {
        try {
            List<Execution> rows = executions.findOwnedBatch(req.getExecutionTokensList(), req.getRequestingUserId());
            GetExecutionResultsBatchResponse.Builder out = GetExecutionResultsBatchResponse.newBuilder();
            rows.forEach(e -> out.addResults(toProto(e)));
            resp.onNext(out.build());
            resp.onCompleted();
        } catch (RuntimeException ex) {
            resp.onError(Status.INTERNAL.withDescription(ex.getMessage()).withCause(ex).asRuntimeException());
        }
    }

    @Override
    public void listLanguages(Empty request, StreamObserver<ListLanguagesResponse> resp) {
        ListLanguagesResponse.Builder out = ListLanguagesResponse.newBuilder();
        for (ExecutionLanguage row : languages.findAll()) {
            out.addLanguages(Language.newBuilder()
                    .setId(row.getId())
                    .setName(row.getName())
                    .setIsActive(row.isActive())
                    .build());
        }
        resp.onNext(out.build());
        resp.onCompleted();
    }

    private ExecutionResult toProto(Execution e) {
        ExecutionResult.Builder b = ExecutionResult.newBuilder()
                .setExecutionToken(e.getJudge0Token())
                .setStatus(nullToEmpty(e.getStatusDescription()))
                .setStatusDescription(nullToEmpty(e.getStatusDescription()))
                .setStdout(nullToEmpty(e.getStdout()))
                .setStderr(nullToEmpty(e.getStderr()))
                .setCompileOutput(nullToEmpty(e.getCompileOutput()))
                .setTimeMs(e.getTimeMs() == null ? 0 : e.getTimeMs())
                .setMemoryKb(e.getMemoryKb() == null ? 0 : e.getMemoryKb())
                .setCreatedAtEpochSeconds(epoch(e.getCreatedAt()))
                .setCompletedAtEpochSeconds(epoch(e.getCompletedAt()));
        return b.build();
    }

    private static long epoch(OffsetDateTime t) {
        return t == null ? 0L : t.toEpochSecond();
    }

    private static String nullToEmpty(String s) {
        return s == null ? "" : s;
    }
}
