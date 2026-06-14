package com.rewabank.loans.exception;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authorization.AuthorizationDeniedException;
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
        when(req.getDescription(false)).thenReturn("uri=/api/v1/loans/apply");
        return req;
    }

    @ParameterizedTest
    @CsvSource({
            "LOAN_001,      404",
            "LOAN_002,      400",
            "LOAN_003,      400",
            "LOAN_004,      400",
            "LOAN_005,      400",
            "LOAN_ACCT_001, 503",
            "LOAN_999,      500"
    })
    void handleLoan_errorCode_mapsToCorrectStatus(String code, int expectedStatus) {
        ResponseEntity<Map<String, Object>> response =
                handler.handleLoan(new LoanException(code, "msg"), mockRequest());
        assertThat(response.getStatusCode().value()).isEqualTo(expectedStatus);
    }

    @Test
    void handleLoan_responseBody_containsAllFields() {
        LoanException ex = new LoanException("LOAN_001", "Loan not found");
        ResponseEntity<Map<String, Object>> response = handler.handleLoan(ex, mockRequest());
        Map<String, Object> body = response.getBody();
        assertThat(body).containsKeys("timestamp", "status", "errorCode", "message", "path");
        assertThat(body.get("errorCode")).isEqualTo("LOAN_001");
        assertThat(body.get("path")).isEqualTo("/api/v1/loans/apply");
        assertThat(body.get("status")).isEqualTo(404);
    }

    @Test
    void handleValidation_returns400WithFieldErrors() {
        BeanPropertyBindingResult binding = new BeanPropertyBindingResult(new Object(), "req");
        binding.addError(new FieldError("req", "amount", "must be positive"));
        MethodArgumentNotValidException ex = new MethodArgumentNotValidException(null, binding);

        ResponseEntity<Map<String, Object>> response = handler.handleValidation(ex, mockRequest());

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).containsEntry("errorCode", "LOAN_VALIDATION");
        @SuppressWarnings("unchecked")
        Map<String, String> fieldErrors = (Map<String, String>) response.getBody().get("fieldErrors");
        assertThat(fieldErrors).containsEntry("amount", "must be positive");
    }

    @Test
    void handleGeneral_returns500WithLoanErrorCode() {
        ResponseEntity<Map<String, Object>> response =
                handler.handleGeneral(new RuntimeException("boom"), mockRequest());
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(response.getBody()).containsEntry("errorCode", "LOAN_500");
    }

    @Test
    void handleAccessDenied_returns403WithLoan403Code() {
        AuthorizationDeniedException ex = new AuthorizationDeniedException("Denied", () -> false);
        ResponseEntity<Map<String, Object>> response =
                handler.handleAccessDenied(ex, mockRequest());
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(response.getBody()).containsEntry("errorCode", "LOAN_403");
        assertThat(response.getBody()).containsEntry("status", 403);
    }
}