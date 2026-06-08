package com.rewabank.gateway.filter;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RequestTraceFilterTest {

    @Spy  FilterUtility      filterUtility;
    @InjectMocks RequestTraceFilter filter;

    @Test
    void filter_noCorrelationId_generatesUuidAndForwardsToChain() {
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/v1/accounts").build());

        GatewayFilterChain chain = mock(GatewayFilterChain.class);
        ArgumentCaptor<ServerWebExchange> captor = ArgumentCaptor.forClass(ServerWebExchange.class);
        when(chain.filter(captor.capture())).thenReturn(Mono.empty());

        StepVerifier.create(filter.filter(exchange, chain)).verifyComplete();

        String correlationId = captor.getValue().getRequest().getHeaders()
                .getFirst(FilterUtility.CORRELATION_ID);
        assertThat(correlationId)
                .isNotNull()
                .isNotBlank()
                .matches("[a-f0-9\\-]{36}");
    }

    @Test
    void filter_correlationIdAlreadyPresent_passesThroughUnchanged() {
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/v1/test")
                        .header(FilterUtility.CORRELATION_ID, "existing-corr-id")
                        .build());

        GatewayFilterChain chain = mock(GatewayFilterChain.class);
        when(chain.filter(any())).thenReturn(Mono.empty());

        StepVerifier.create(filter.filter(exchange, chain)).verifyComplete();

        // filterUtility.setCorrelationId should NOT be called when ID already present
        verify(filterUtility, never()).setCorrelationId(any(), any());
    }

    @Test
    void filter_generatesNewIdEachRequest() {
        GatewayFilterChain chain = mock(GatewayFilterChain.class);
        ArgumentCaptor<ServerWebExchange> captor = ArgumentCaptor.forClass(ServerWebExchange.class);
        when(chain.filter(captor.capture())).thenReturn(Mono.empty());

        MockServerWebExchange e1 = MockServerWebExchange.from(MockServerHttpRequest.get("/a").build());
        MockServerWebExchange e2 = MockServerWebExchange.from(MockServerHttpRequest.get("/b").build());

        StepVerifier.create(filter.filter(e1, chain)).verifyComplete();
        String id1 = captor.getValue().getRequest().getHeaders().getFirst(FilterUtility.CORRELATION_ID);

        StepVerifier.create(filter.filter(e2, chain)).verifyComplete();
        String id2 = captor.getValue().getRequest().getHeaders().getFirst(FilterUtility.CORRELATION_ID);

        assertThat(id1).isNotEqualTo(id2);
    }
}
