package com.rewabank.gateway.exception;

import org.springframework.boot.web.reactive.error.ErrorWebExceptionHandler;
import org.springframework.core.annotation.Order;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.server.ServerWebExchange;
import com.fasterxml.jackson.databind.ObjectMapper;
import reactor.core.publisher.Mono;
import java.time.LocalDateTime;
import java.util.Map;

@Order(-1)
@Component
public class GlobalExceptionHandler implements ErrorWebExceptionHandler {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public Mono<Void> handle(ServerWebExchange exchange, Throwable ex) {
        HttpStatus status;
        String errorCode;
        String message;

        if (ex instanceof AuthenticationException) {
            status    = HttpStatus.UNAUTHORIZED;
            errorCode = "GW_AUTH_001";
            message   = "Authentication required";
        } else if (ex instanceof AccessDeniedException) {
            status    = HttpStatus.FORBIDDEN;
            errorCode = "GW_AUTH_002";
            message   = "Insufficient permissions";
        } else if (ex instanceof ResponseStatusException rse
                && rse.getStatusCode() == HttpStatus.TOO_MANY_REQUESTS) {
            status    = HttpStatus.TOO_MANY_REQUESTS;
            errorCode = "GW_RATE_001";
            message   = "Too many requests. Please slow down.";
        } else if (ex instanceof ResponseStatusException rse
                && rse.getStatusCode() == HttpStatus.SERVICE_UNAVAILABLE) {
            status    = HttpStatus.SERVICE_UNAVAILABLE;
            errorCode = "GW_CB_001";
            message   = "Service temporarily unavailable";
        } else {
            status    = HttpStatus.INTERNAL_SERVER_ERROR;
            errorCode = "GW_ERR_001";
            message   = "An unexpected error occurred";
        }

        exchange.getResponse().setStatusCode(status);
        exchange.getResponse().getHeaders().setContentType(MediaType.APPLICATION_JSON);

        Map<String, Object> body = Map.of(
                "timestamp",     LocalDateTime.now().toString(),
                "status",        status.value(),
                "errorCode",     errorCode,
                "message",       message,
                "path",          exchange.getRequest().getPath().toString(),
                "correlationId", exchange.getRequest().getHeaders()
                        .getFirst("rewabank-correlation-id") != null
                        ? exchange.getRequest().getHeaders().getFirst("rewabank-correlation-id")
                        : "unknown"
        );

        try {
            byte[] bytes = objectMapper.writeValueAsBytes(body);
            DataBuffer buffer = exchange.getResponse().bufferFactory().wrap(bytes);
            return exchange.getResponse().writeWith(Mono.just(buffer));
        } catch (Exception e) {
            return Mono.error(e);
        }
    }
}
