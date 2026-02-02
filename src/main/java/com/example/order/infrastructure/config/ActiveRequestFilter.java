package com.example.order.infrastructure.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

/**
 * WebFilter that tracks active requests for graceful shutdown.
 * Part of Strategy 1: Graceful Shutdown.
 */
@Component
@Order(1)
public class ActiveRequestFilter implements WebFilter {

    private static final Logger log = LoggerFactory.getLogger(ActiveRequestFilter.class);

    private final GracefulShutdownConfig gracefulShutdownConfig;

    public ActiveRequestFilter(GracefulShutdownConfig gracefulShutdownConfig) {
        this.gracefulShutdownConfig = gracefulShutdownConfig;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        String path = exchange.getRequest().getPath().value();

        // Skip actuator endpoints for request tracking
        if (path.startsWith("/actuator") || path.startsWith("/h2-console")) {
            return chain.filter(exchange);
        }

        gracefulShutdownConfig.incrementActiveRequests();

        return chain.filter(exchange)
                .doFinally(signalType -> {
                    gracefulShutdownConfig.decrementActiveRequests();
                    log.debug("Request to {} completed with signal: {}", path, signalType);
                });
    }
}
