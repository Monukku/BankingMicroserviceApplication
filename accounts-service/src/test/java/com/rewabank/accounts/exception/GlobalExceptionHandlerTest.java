package com.rewabank.accounts.exception;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.dao.DataIntegrityViolationException;
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
        when(req.getDescription(false)).thenReturn("uri=/api/v1/accounts");
        return req;
    }

    @ParameterizedTest
    @CsvSource({
            "ACCT_002,     404",
            "ACCT_001,     400",
            "ACCT_003,     400",
            "ACCT_004,     400",
            "ACCT_005,     400",
            "ACCT_006,     400",
            "ACCT_007,     400",
            "ACCT_008,     400",
            "ACCT_KYC_001, 503",
            "ACCT_999,     500"
    })
    void handleAccount_errorCode_mapsToCorrectStatus(String code, int expectedStatus) {
        ResponseEntity<Map<String, Object>> response =
                handler.handleAccount(new AccountException(code, "msg"), mockRequest());
        assertThat(response.getStatusCode().value()).isEqualTo(expectedStatus);
    }

    @Test
    void handleAccount_responseBody_containsAllFields() {
        AccountException ex = new AccountException("ACCT_002", "Account not found");
        ResponseEntity<Map<String, Object>> response = handler.handleAccount(ex, mockRequest());
        Map<String, Object> body = response.getBody();
        assertThat(body).containsKeys("timestamp", "status", "errorCode", "message", "path");
        assertThat(body.get("errorCode")).isEqualTo("ACCT_002");
        assertThat(body.get("status")).isEqualTo(404);
        assertThat(body.get("path")).isEqualTo("/api/v1/accounts");
    }

    @Test
    void handleDataIntegrity_returns409WithConflictCode() {
        DataIntegrityViolationException ex =
                new DataIntegrityViolationException("Duplicate key");
        ResponseEntity<Map<String, Object>> response =
                handler.handleDataIntegrity(ex, mockRequest());
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(response.getBody()).containsEntry("errorCode", "ACCT_CONFLICT");
        assertThat(response.getBody()).containsEntry("status", 409);
    }

    @Test
    void handleValidation_returns400WithFieldErrors() {
        BeanPropertyBindingResult binding = new BeanPropertyBindingResult(new Object(), "req");
        binding.addError(new FieldError("req", "accountType", "must not be null"));
        MethodArgumentNotValidException ex = new MethodArgumentNotValidException(null, binding);

        ResponseEntity<Map<String, Object>> response = handler.handleValidation(ex, mockRequest());

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).containsEntry("errorCode", "ACCT_VALIDATION");
        @SuppressWarnings("unchecked")
        Map<String, String> fieldErrors = (Map<String, String>) response.getBody().get("fieldErrors");
        assertThat(fieldErrors).containsEntry("accountType", "must not be null");
    }

    @Test
    void handleGeneral_returns500WithAcctErrorCode() {
        ResponseEntity<Map<String, Object>> response =
                handler.handleGeneral(new RuntimeException("unexpected"), mockRequest());
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(response.getBody()).containsEntry("errorCode", "ACCT_500");
    }
}