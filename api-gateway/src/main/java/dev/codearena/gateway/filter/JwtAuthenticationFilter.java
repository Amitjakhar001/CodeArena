package dev.codearena.gateway.filter;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.codearena.gateway.config.JwtProperties;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jws;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.JwtException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

/**
 * Validates the Bearer JWT on every request, extracts the userId claim, and
 * injects it as X-User-Id for downstream services to trust. Public bypass paths
 * skip validation entirely so registration/login can work without a token.
 *
 * Ordered just below CorrelationIdFilter so that any 401 response we emit still
 * carries the correlation id.
 */
@Component
public class JwtAuthenticationFilter implements GlobalFilter, Ordered {

    private static final Logger log = LoggerFactory.getLogger(JwtAuthenticationFilter.class);
    private static final String USER_ID_HEADER = "X-User-Id";

    private static final List<String> BYPASS_PREFIXES = List.of(
        "/api/v1/auth/register",
        "/api/v1/auth/login",
        "/api/v1/auth/refresh",
        "/actuator/health",
        "/actuator/info"
    );

    private final SecretKey signingKey;
    private final ObjectMapper json;

    public JwtAuthenticationFilter(JwtProperties props, ObjectMapper json) {
        this.signingKey = new SecretKeySpec(
            props.secret().getBytes(StandardCharsets.UTF_8), "HmacSHA256");
        this.json = json;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        ServerHttpRequest request = exchange.getRequest();
        String path = request.getURI().getPath();

        if (isBypassed(path)) {
            // Strip any client-supplied X-User-Id on public paths so it can never be spoofed.
            ServerHttpRequest sanitized = request.mutate().headers(h -> h.remove(USER_ID_HEADER)).build();
            return chain.filter(exchange.mutate().request(sanitized).build());
        }

        String authHeader = request.getHeaders().getFirst(HttpHeaders.AUTHORIZATION);
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            return reject(exchange, "Missing or malformed Authorization header");
        }

        String token = authHeader.substring(7);
        Jws<Claims> parsed;
        try {
            parsed = Jwts.parser().verifyWith(signingKey).build().parseSignedClaims(token);
        } catch (JwtException e) {
            log.debug("JWT validation failed for {}: {}", path, e.getMessage());
            return reject(exchange, "Invalid or expired token");
        }

        String userId = parsed.getPayload().getSubject();
        if (userId == null || userId.isBlank()) {
            return reject(exchange, "Token missing subject claim");
        }

        // Trust boundary: from here on, downstream services consume X-User-Id
        // without validating the JWT themselves.
        ServerHttpRequest mutated = request.mutate()
            .header(USER_ID_HEADER, userId)
            .build();
        return chain.filter(exchange.mutate().request(mutated).build());
    }

    private boolean isBypassed(String path) {
        for (String prefix : BYPASS_PREFIXES) {
            if (path.equals(prefix) || path.startsWith(prefix + "/")) return true;
        }
        return false;
    }

    private Mono<Void> reject(ServerWebExchange exchange, String detail) {
        ServerHttpResponse response = exchange.getResponse();
        response.setStatusCode(HttpStatus.UNAUTHORIZED);
        response.getHeaders().setContentType(MediaType.APPLICATION_PROBLEM_JSON);

        String correlationId = exchange.getRequest().getHeaders().getFirst("X-Correlation-Id");
        Map<String, Object> body = new java.util.LinkedHashMap<>();
        body.put("type", "https://codearena.dev/errors/unauthorized");
        body.put("title", "Unauthorized");
        body.put("status", 401);
        body.put("detail", detail);
        body.put("instance", exchange.getRequest().getURI().getPath());
        if (correlationId != null) body.put("correlationId", correlationId);

        byte[] bytes;
        try {
            bytes = json.writeValueAsBytes(body);
        } catch (Exception e) {
            bytes = ("{\"title\":\"Unauthorized\",\"status\":401,\"detail\":\""
                + detail.replace("\"", "'") + "\"}").getBytes(StandardCharsets.UTF_8);
        }
        DataBuffer buffer = response.bufferFactory().wrap(bytes);
        return response.writeWith(Mono.just(buffer));
    }

    @Override
    public int getOrder() {
        // Run after CorrelationIdFilter (HIGHEST_PRECEDENCE + 1) but before
        // built-in filters and the rate limiter.
        return Ordered.HIGHEST_PRECEDENCE + 10;
    }
}
