package com.gateway.apigateway.Filter;

import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;
import org.springframework.http.HttpHeaders;
import java.util.UUID;
import org.slf4j.Logger;

@Order(1)
@Component
public class RequestTraceFilter implements GlobalFilter {
    private static final Logger logger= LoggerFactory.getLogger(RequestTraceFilter.class);
    @Autowired
    FilterUtility filterUtility;

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {

        HttpHeaders requestHeaders = exchange.getRequest().getHeaders();
        String correlationId;
        if (isCorrelationIdPresent(requestHeaders)) {
            logger.debug("rewabank-correlation-Id found in RequestTraceFilter:{}", filterUtility.getCorrelationId(requestHeaders));
            filterUtility.getCorrelationId(requestHeaders);
        } else {

            correlationId = generateCorrelationId();
            filterUtility.setCorrelationId(exchange, correlationId);
            logger.debug("rewabank-correlation-Id is generated in RequestTraceFilter:{}", correlationId);
        }
        return chain.filter(exchange);
    }

    private boolean isCorrelationIdPresent(HttpHeaders requestHeaders) {
        if (filterUtility.getCorrelationId(requestHeaders) != null) {
            return true;
        } else {
            return false;
        }
    }

    private String generateCorrelationId(){
        return UUID.randomUUID().toString();
    }
}

