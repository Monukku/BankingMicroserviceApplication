package com.rewabank.gateway.filter;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.annotation.Order;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

@Order(2)
@Component
public class AuthenticationFilter implements GlobalFilter {

    private static final Logger logger = LoggerFactory.getLogger(AuthenticationFilter.class);

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        return ReactiveSecurityContextHolder.getContext()
                .flatMap(ctx -> {
                    if (ctx.getAuthentication() instanceof JwtAuthenticationToken jwtAuth) {
                        Jwt jwt = jwtAuth.getToken();

                        String userId = jwt.getSubject();
                        String email  = jwt.getClaimAsString("email");
                        String roles  = String.join(",", jwtAuth.getAuthorities()
                                .stream()
                                .map(a -> a.getAuthority().replace("ROLE_", ""))
                                .toList());

                        logger.debug("Injecting headers — userId: {}, roles: {}", userId, roles);

                        // Downstream MS read these headers — they never re-validate JWT
                        ServerWebExchange mutated = exchange.mutate()
                                .request(r -> r
                                        .header("X-User-Id",    userId != null ? userId : "")
                                        .header("X-User-Email", email  != null ? email  : "")
                                        .header("X-User-Role",  roles))
                                .build();

                        return chain.filter(mutated);
                    }
                    return chain.filter(exchange);
                })
                .switchIfEmpty(chain.filter(exchange));
    }
}
