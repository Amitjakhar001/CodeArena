package dev.codearena.auth.grpc;

import dev.codearena.auth.domain.User;
import dev.codearena.auth.repository.UserRepository;
import dev.codearena.auth.service.JwtService;
import dev.codearena.proto.auth.v1.AuthInternalGrpc;
import dev.codearena.proto.auth.v1.GetUserRequest;
import dev.codearena.proto.auth.v1.GetUserResponse;
import dev.codearena.proto.auth.v1.ValidateTokenRequest;
import dev.codearena.proto.auth.v1.ValidateTokenResponse;
import dev.codearena.proto.common.v1.UserContext;
import io.grpc.stub.StreamObserver;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import net.devh.boot.grpc.server.service.GrpcService;
import org.bson.types.ObjectId;

import java.util.Optional;

@GrpcService
public class AuthInternalGrpcService extends AuthInternalGrpc.AuthInternalImplBase {

    private final JwtService jwt;
    private final UserRepository users;

    public AuthInternalGrpcService(JwtService jwt, UserRepository users) {
        this.jwt = jwt;
        this.users = users;
    }

    @Override
    public void validateToken(ValidateTokenRequest req, StreamObserver<ValidateTokenResponse> resp) {
        ValidateTokenResponse.Builder builder = ValidateTokenResponse.newBuilder();
        try {
            Claims claims = jwt.parse(req.getToken());
            UserContext ctx = UserContext.newBuilder()
                    .setUserId(nullToEmpty(claims.getSubject()))
                    .setUsername(nullToEmpty(claims.get("username", String.class)))
                    .setRole(nullToEmpty(claims.get("role", String.class)))
                    .build();
            builder.setValid(true)
                    .setUser(ctx)
                    .setExpiresAtEpochSeconds(claims.getExpiration().toInstant().getEpochSecond());
        } catch (JwtException e) {
            builder.setValid(false);
        }
        resp.onNext(builder.build());
        resp.onCompleted();
    }

    @Override
    public void getUser(GetUserRequest req, StreamObserver<GetUserResponse> resp) {
        GetUserResponse.Builder builder = GetUserResponse.newBuilder();
        try {
            ObjectId id = new ObjectId(req.getUserId());
            Optional<User> opt = users.findById(id);
            if (opt.isPresent()) {
                User u = opt.get();
                builder.setFound(true)
                        .setUser(UserContext.newBuilder()
                                .setUserId(u.getId().toHexString())
                                .setUsername(u.getUsername())
                                .setRole(u.getRole().name())
                                .build());
            } else {
                builder.setFound(false);
            }
        } catch (IllegalArgumentException e) {
            builder.setFound(false);
        }
        resp.onNext(builder.build());
        resp.onCompleted();
    }

    private static String nullToEmpty(String s) {
        return s == null ? "" : s;
    }
}
