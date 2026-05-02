package dev.codearena.gateway.filter;

import org.slf4j.MDC;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.UUID;

/**
 * Ensures every request — incoming and outgoing — carries an X-Correlation-Id.
 * If the client sent one, we keep it; otherwise we generate one and propagate it
 * downstream + into the response. Also pushed into MDC so the gateway's own logs
 * are correlatable. Runs at highest precedence so the id is set before any other
 * filter (including JWT auth) emits a log line or error response.
 */
@Component
public class CorrelationIdFilter implements GlobalFilter, Ordered {

    public static final String HEADER = "X-Correlation-Id";
    private static final String MDC_KEY = "correlationId";

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        ServerHttpRequest request = exchange.getRequest();
        String correlationId = request.getHeaders().getFirst(HEADER);
        if (correlationId == null || correlationId.isBlank()) {
            correlationId = UUID.randomUUID().toString();
        }
        final String id = correlationId;

        ServerHttpRequest mutatedRequest = request.mutate().header(HEADER, id).build();
        exchange.getResponse().getHeaders().add(HEADER, id);

        ServerWebExchange mutatedExchange = exchange.mutate().request(mutatedRequest).build();

        // MDC works per-thread; reactor pipelines hop threads. Set it for the
        // duration of this synchronous segment, then clear. For richer reactive
        // logging context we'd use Reactor's ContextView — out of scope here.
        return Mono.fromRunnable(() -> MDC.put(MDC_KEY, id))
            .then(chain.filter(mutatedExchange))
            .doFinally(s -> MDC.remove(MDC_KEY));
    }

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE;
    }
}
