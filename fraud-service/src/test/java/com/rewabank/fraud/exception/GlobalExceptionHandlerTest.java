package com.rewabank.fraud.exception;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.context.request.WebRequest;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    private WebRequest mockRequest() {
        WebRequest req = mock(WebRequest.class);
        when(req.getDescription(false)).thenReturn("uri=/api/v1/fraud/score");
        return req;
    }

    @Test
    void handleFraud_returns400WithErrorCode() {
        FraudException ex = new FraudException("FRAUD_001", "Score threshold exceeded");
        ResponseEntity<Map<String, Object>> response =
                handler.handleFraud(ex, mockRequest());
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).containsEntry("errorCode", "FRAUD_001");
        assertThat(response.getBody()).containsEntry("message", "Score threshold exceeded");
        assertThat(response.getBody()).containsEntry("status", 400);
    }

    @Test
    void handleFraud_responseBodyHasTimestamp() {
        ResponseEntity<Map<String, Object>> response =
                handler.handleFraud(new FraudException("FRAUD_002", "msg"), mockRequest());
        assertThat(response.getBody()).containsKey("timestamp");
    }

    @Test
    void handleGeneral_returns500WithFraudErrorCode() {
        ResponseEntity<Map<String, Object>> response =
                handler.handleGeneral(new RuntimeException("unexpected"), mockRequest());
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(response.getBody()).containsEntry("errorCode", "FRAUD_500");
        assertThat(response.getBody()).containsEntry("status", 500);
        assertThat(response.getBody()).containsEntry("message", "An unexpected error occurred");
    }
}