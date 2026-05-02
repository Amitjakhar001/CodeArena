package dev.codearena.app.grpc;

import dev.codearena.proto.auth.v1.AuthInternalGrpc;
import dev.codearena.proto.auth.v1.GetUserRequest;
import dev.codearena.proto.auth.v1.GetUserResponse;
import dev.codearena.proto.common.v1.UserContext;
import io.grpc.StatusRuntimeException;
import net.devh.boot.grpc.client.inject.GrpcClient;
import org.springframework.stereotype.Component;

import java.util.Optional;

@Component
public class AuthServiceClient {

    @GrpcClient("auth-service")
    private AuthInternalGrpc.AuthInternalBlockingStub stub;

    public Optional<UserContext> getUser(String userId) {
        try {
            GetUserResponse resp = stub.getUser(GetUserRequest.newBuilder().setUserId(userId).build());
            return resp.getFound() ? Optional.of(resp.getUser()) : Optional.empty();
        } catch (StatusRuntimeException e) {
            throw new GrpcUpstreamException("auth-service.GetUser failed: " + e.getStatus(), e);
        }
    }
}
