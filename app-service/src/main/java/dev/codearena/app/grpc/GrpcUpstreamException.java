package dev.codearena.app.grpc;

public class GrpcUpstreamException extends RuntimeException {
    public GrpcUpstreamException(String message, Throwable cause) {
        super(message, cause);
    }
}
