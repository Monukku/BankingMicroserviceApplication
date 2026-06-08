package com.rewabank.gateway.filter;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.ServerWebExchange;

import static org.assertj.core.api.Assertions.assertThat;

class FilterUtilityTest {

    private final FilterUtility filterUtility = new FilterUtility();

    @Test
    void getCorrelationId_whenPresent_returnsValue() {
        HttpHeaders headers = new HttpHeaders();
        headers.add(FilterUtility.CORRELATION_ID, "corr-abc-123");

        assertThat(filterUtility.getCorrelationId(headers)).isEqualTo("corr-abc-123");
    }

    @Test
    void getCorrelationId_whenAbsent_returnsNull() {
        assertThat(filterUtility.getCorrelationId(new HttpHeaders())).isNull();
    }

    @Test
    void setCorrelationId_addsHeaderToMutatedExchange() {
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/v1/test").build());

        ServerWebExchange mutated = filterUtility.setCorrelationId(exchange, "new-corr-id");

        assertThat(mutated.getRequest().getHeaders()
                .getFirst(FilterUtility.CORRELATION_ID))
                .isEqualTo("new-corr-id");
    }

    @Test
    void setRequestHeader_addsArbitraryHeader() {
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/test").build());

        ServerWebExchange mutated = filterUtility.setRequestHeader(exchange, "X-Test", "value");

        assertThat(mutated.getRequest().getHeaders().getFirst("X-Test")).isEqualTo("value");
    }

    @Test
    void correlationIdConstant_hasExpectedValue() {
        assertThat(FilterUtility.CORRELATION_ID).isEqualTo("rewabank-correlation-id");
    }
}
