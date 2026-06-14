package com.rewabank.transactions.exception;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.context.request.WebRequest;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    private WebRequest mockRequest() {
        WebRequest req = mock(WebRequest.class);
        when(req.getDescription(false)).thenReturn("uri=/api/v1/transactions/transfer");
        return req;
    }

    @ParameterizedTest
    @CsvSource({
            "TXN_007,      404",
            "TXN_001,      409",
            "TXN_FRAUD_001,403",
            "TXN_ACCT_001, 503",
            "TXN_ACCT_002, 503",
            "TXN_002,      400",
            "TXN_999,      400"
    })
    void handleTransaction_errorCode_mapsToCorrectStatus(String code, int expectedStatus) {
        ResponseEntity<Map<String, Object>> response =
                handler.handleTransaction(new TransactionException(code, "msg"), mockRequest());
        assertThat(response.getStatusCode().value()).isEqualTo(expectedStatus);
    }

    @Test
    void handleTransaction_responseBody_containsRequiredFields() {
        TransactionException ex = new TransactionException("TXN_007", "Transaction not found");
        ResponseEntity<Map<String, Object>> response =
                handler.handleTransaction(ex, mockRequest());
        Map<String, Object> body = response.getBody();
        assertThat(body).containsKeys("timestamp", "status", "errorCode", "message", "path");
        assertThat(body.get("errorCode")).isEqualTo("TXN_007");
        assertThat(body.get("status")).isEqualTo(404);
        assertThat(body.get("path")).isEqualTo("/api/v1/transactions/transfer");
    }

    @Test
    void handleValidation_returns400WithFieldErrors() {
        BeanPropertyBindingResult binding = new BeanPropertyBindingResult(new Object(), "req");
        binding.addError(new FieldError("req", "amount", "must be positive"));
        MethodArgumentNotValidException ex = new MethodArgumentNotValidException(null, binding);

        ResponseEntity<Map<String, Object>> response = handler.handleValidation(ex, mockRequest());

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).containsEntry("errorCode", "TXN_VALIDATION");
        @SuppressWarnings("unchecked")
        Map<String, String> fieldErrors = (Map<String, String>) response.getBody().get("fieldErrors");
        assertThat(fieldErrors).containsEntry("amount", "must be positive");
    }

    @Test
    void handleGeneral_returns500WithTxnErrorCode() {
        ResponseEntity<Map<String, Object>> response =
                handler.handleGeneral(new RuntimeException("unexpected"), mockRequest());
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(response.getBody()).containsEntry("errorCode", "TXN_500");
        assertThat(response.getBody()).containsEntry("status", 500);
    }
}