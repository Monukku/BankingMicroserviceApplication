package com.rewabank.gateway.filter;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ResponseTraceFilterTest {

    @Spy FilterUtility filterUtility;
    @InjectMocks ResponseTraceFilter responseTraceFilter;

    @Test
    void postGlobalFilter_returnsNonNullFilter() {
        GlobalFilter filter = responseTraceFilter.postGlobalFilter();
        assertThat(filter).isNotNull();
    }

    @Test
    void postGlobalFilter_withCorrelationId_addsItToResponseHeader() {
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/v1/test")
                        .header(FilterUtility.CORRELATION_ID, "corr-xyz-789")
                        .build());
        GatewayFilterChain chain = mock(GatewayFilterChain.class);
        when(chain.filter(any())).thenReturn(Mono.empty());

        GlobalFilter filter = responseTraceFilter.postGlobalFilter();
        StepVerifier.create(filter.filter(exchange, chain)).verifyComplete();

        assertThat(exchange.getResponse().getHeaders().getFirst(FilterUtility.CORRELATION_ID))
                .isEqualTo("corr-xyz-789");
    }

    @Test
    void postGlobalFilter_withoutCorrelationId_addsNullToResponseHeader() {
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/v1/test").build());
        GatewayFilterChain chain = mock(GatewayFilterChain.class);
        when(chain.filter(any())).thenReturn(Mono.empty());

        GlobalFilter filter = responseTraceFilter.postGlobalFilter();
        StepVerifier.create(filter.filter(exchange, chain)).verifyComplete();

        // null correlationId from request — header add is still called
        assertThat(exchange.getResponse().getHeaders().containsKey(FilterUtility.CORRELATION_ID))
                .isTrue();
    }
}