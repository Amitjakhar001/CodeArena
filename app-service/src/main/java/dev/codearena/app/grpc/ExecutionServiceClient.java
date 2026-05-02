package dev.codearena.app.grpc;

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
import io.grpc.StatusRuntimeException;
import net.devh.boot.grpc.client.inject.GrpcClient;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

@Component
public class ExecutionServiceClient {

    @GrpcClient("execution-service")
    private ExecutionServiceGrpc.ExecutionServiceBlockingStub stub;

    public SubmitCodeResponse submitCode(SubmitCodeRequest request) {
        try {
            return stub.submitCode(request);
        } catch (StatusRuntimeException e) {
            throw new GrpcUpstreamException("execution-service.SubmitCode failed: " + e.getStatus(), e);
        }
    }

    public Optional<ExecutionResult> getExecutionResult(String token, String requestingUserId) {
        try {
            GetExecutionResultResponse resp = stub.getExecutionResult(
                GetExecutionResultRequest.newBuilder()
                    .setExecutionToken(token)
                    .setRequestingUserId(requestingUserId)
                    .build());
            return resp.getFound() ? Optional.of(resp.getResult()) : Optional.empty();
        } catch (StatusRuntimeException e) {
            throw new GrpcUpstreamException("execution-service.GetExecutionResult failed: " + e.getStatus(), e);
        }
    }

    public List<ExecutionResult> getExecutionResultsBatch(List<String> tokens, String requestingUserId) {
        try {
            GetExecutionResultsBatchResponse resp = stub.getExecutionResultsBatch(
                GetExecutionResultsBatchRequest.newBuilder()
                    .addAllExecutionTokens(tokens)
                    .setRequestingUserId(requestingUserId)
                    .build());
            return resp.getResultsList();
        } catch (StatusRuntimeException e) {
            throw new GrpcUpstreamException("execution-service.GetExecutionResultsBatch failed: " + e.getStatus(), e);
        }
    }

    public List<Language> listLanguages() {
        try {
            ListLanguagesResponse resp = stub.listLanguages(Empty.newBuilder().build());
            return resp.getLanguagesList();
        } catch (StatusRuntimeException e) {
            throw new GrpcUpstreamException("execution-service.ListLanguages failed: " + e.getStatus(), e);
        }
    }
}
