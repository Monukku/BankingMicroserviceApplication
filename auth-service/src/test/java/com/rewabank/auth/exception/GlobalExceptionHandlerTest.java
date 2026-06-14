package com.rewabank.auth.exception;

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
        when(req.getDescription(false)).thenReturn("uri=/api/v1/auth/test");
        return req;
    }

    // ── handleAuthException — HTTP status mapping ─────────────────────────────

    @ParameterizedTest
    @CsvSource({
            "AUTH_001, 409",
            "AUTH_003, 404",
            "AUTH_004, 429",
            "AUTH_005, 429",
            "AUTH_006, 400",
            "AUTH_007, 400",
            "AUTH_999, 500"
    })
    void handleAuthException_errorCode_mapsToCorrectStatus(String code, int expectedStatus) {
        ResponseEntity<Map<String, Object>> response =
                handler.handleAuthException(new AuthException(code, "msg"), mockRequest());
        assertThat(response.getStatusCode().value()).isEqualTo(expectedStatus);
    }

    @Test
    void handleAuthException_responseBody_containsAllFields() {
        AuthException ex = new AuthException("AUTH_001", "Email already registered");
        ResponseEntity<Map<String, Object>> response =
                handler.handleAuthException(ex, mockRequest());
        Map<String, Object> body = response.getBody();
        assertThat(body).containsKeys("timestamp", "status", "errorCode", "message", "path");
        assertThat(body.get("errorCode")).isEqualTo("AUTH_001");
        assertThat(body.get("message")).isEqualTo("Email already registered");
        assertThat(body.get("status")).isEqualTo(409);
        assertThat(body.get("path")).isEqualTo("/api/v1/auth/test");
    }

    // ── handleValidation ──────────────────────────────────────────────────────

    @Test
    void handleValidation_returns400WithFieldErrors() {
        BeanPropertyBindingResult binding = new BeanPropertyBindingResult(new Object(), "req");
        binding.addError(new FieldError("req", "email", "must not be blank"));
        MethodArgumentNotValidException ex = new MethodArgumentNotValidException(null, binding);

        ResponseEntity<Map<String, Object>> response =
                handler.handleValidation(ex, mockRequest());

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).containsEntry("errorCode", "AUTH_VALIDATION");
        @SuppressWarnings("unchecked")
        Map<String, String> fieldErrors = (Map<String, String>) response.getBody().get("fieldErrors");
        assertThat(fieldErrors).containsEntry("email", "must not be blank");
    }

    @Test
    void handleValidation_nullMessage_usesInvalidValueFallback() {
        BeanPropertyBindingResult binding = new BeanPropertyBindingResult(new Object(), "req");
        binding.addError(new FieldError("req", "mobile", null));
        MethodArgumentNotValidException ex = new MethodArgumentNotValidException(null, binding);

        ResponseEntity<Map<String, Object>> response =
                handler.handleValidation(ex, mockRequest());

        @SuppressWarnings("unchecked")
        Map<String, String> fieldErrors = (Map<String, String>) response.getBody().get("fieldErrors");
        assertThat(fieldErrors).containsEntry("mobile", "Invalid value");
    }

    // ── handleGeneral ─────────────────────────────────────────────────────────

    @Test
    void handleGeneral_returns500WithAuthErrorCode() {
        ResponseEntity<Map<String, Object>> response =
                handler.handleGeneral(new RuntimeException("boom"), mockRequest());
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(response.getBody()).containsEntry("errorCode", "AUTH_500");
        assertThat(response.getBody()).containsEntry("status", 500);
    }
}