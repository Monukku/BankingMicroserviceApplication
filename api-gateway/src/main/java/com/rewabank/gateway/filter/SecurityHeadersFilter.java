package com.rewabank.gateway.filter;

import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

@Order(3)
@Component
public class SecurityHeadersFilter implements GlobalFilter {

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        return chain.filter(exchange).then(Mono.fromRunnable(() -> {
            HttpHeaders headers = exchange.getResponse().getHeaders();
            headers.add("X-Content-Type-Options",  "nosniff");
            headers.add("X-Frame-Options",          "DENY");
            headers.add("X-XSS-Protection",         "1; mode=block");
            headers.add("Strict-Transport-Security","max-age=31536000; includeSubDomains");
            headers.add("Cache-Control",            "no-store, no-cache, must-revalidate");
            headers.add("Pragma",                   "no-cache");
            headers.add("Content-Security-Policy",
                    "default-src 'self'; frame-ancestors 'none'; form-action 'self'");
            headers.add("Referrer-Policy",          "strict-origin-when-cross-origin");
        }));
    }
}
