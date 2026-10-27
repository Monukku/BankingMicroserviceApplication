package com.rewabank.gateway.filter; // ← CHANGED

import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.http.HttpHeaders;
import java.util.List;

@Component
public class FilterUtility {
    public static final String CORRELATION_ID = "rewabank-correlation-id";

    public String getCorrelationId(HttpHeaders requestHeaders) {
        List<String> requestHeadersList = requestHeaders.get(CORRELATION_ID);
        if (requestHeadersList == null) {
            return null;
        }
        return requestHeadersList.stream().findFirst().orElse(null);
    }

    public ServerWebExchange setRequestHeader(ServerWebExchange exchange, String name, String value) {
        return exchange.mutate()
                .request(exchange.getRequest().mutate().header(name, value).build())
                .build();
    }

    public ServerWebExchange setCorrelationId(ServerWebExchange exchange, String correlationId) {
        return this.setRequestHeader(exchange, CORRELATION_ID, correlationId);
    }
}
