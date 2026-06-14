package com.rewabank.customers.exception;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.BindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.context.request.WebRequest;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class GlobalExceptionHandlerTest {

    private GlobalExceptionHandler handler;
    private WebRequest request;

    @BeforeEach
    void setUp() {
        handler = new GlobalExceptionHandler();
        request = mock(WebRequest.class);
        when(request.getDescription(false)).thenReturn("uri=/api/v1/test");
    }

    // ── handleCustomer — HTTP status mapping ──────────────────────────────────

    @ParameterizedTest
    @CsvSource({
            "CUST_001, 404",
            "CUST_002, 409",
            "CUST_003, 409",
            "CUST_004, 400",
            "CUST_005, 400",
            "CUST_006, 400",
            "CUST_007, 400",
            "CUST_008, 400",
            "CUST_009, 400",
            "CUST_010, 500",
            "CUST_999, 500"
    })
    void handleCustomer_errorCode_mapsToCorrectStatus(String code, int expectedStatus) {
        ResponseEntity<Map<String, Object>> response =
                handler.handleCustomer(new CustomerException(code, "msg"), request);
        assertThat(response.getStatusCode().value()).isEqualTo(expectedStatus);
    }

    @Test
    void handleCustomer_responseBody_containsAllRequiredFields() {
        CustomerException ex = new CustomerException("CUST_001", "Customer not found");
        ResponseEntity<Map<String, Object>> response = handler.handleCustomer(ex, request);
        Map<String, Object> body = response.getBody();
        assertThat(body).containsKeys("timestamp", "status", "errorCode", "message", "path");
        assertThat(body.get("errorCode")).isEqualTo("CUST_001");
        assertThat(body.get("message")).isEqualTo("Customer not found");
        assertThat(body.get("status")).isEqualTo(404);
        assertThat(body.get("path")).isEqualTo("/api/v1/test");
    }

    // ── handleValidation ──────────────────────────────────────────────────────

    @Test
    void handleValidation_fieldErrorWithMessage_includesItInResponse() {
        MethodArgumentNotValidException ex = mock(MethodArgumentNotValidException.class);
        BindingResult bindingResult = mock(BindingResult.class);
        when(ex.getBindingResult()).thenReturn(bindingResult);
        when(bindingResult.getFieldErrors()).thenReturn(
                List.of(new FieldError("req", "aadhaarNumber", "must not be blank")));

        ResponseEntity<Map<String, Object>> response = handler.handleValidation(ex, request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        @SuppressWarnings("unchecked")
        Map<String, String> fieldErrors = (Map<String, String>) response.getBody().get("fieldErrors");
        assertThat(fieldErrors).containsEntry("aadhaarNumber", "must not be blank");
    }

    @Test
    void handleValidation_fieldErrorWithNullMessage_fallsBackToInvalid() {
        // Kills NegateConditionals on: defaultMessage != null ? defaultMessage : "Invalid"
        MethodArgumentNotValidException ex = mock(MethodArgumentNotValidException.class);
        BindingResult bindingResult = mock(BindingResult.class);
        when(ex.getBindingResult()).thenReturn(bindingResult);
        when(bindingResult.getFieldErrors()).thenReturn(
                List.of(new FieldError("req", "panNumber", null)));

        ResponseEntity<Map<String, Object>> response = handler.handleValidation(ex, request);

        @SuppressWarnings("unchecked")
        Map<String, String> fieldErrors = (Map<String, String>) response.getBody().get("fieldErrors");
        assertThat(fieldErrors).containsEntry("panNumber", "Invalid");
    }

    @Test
    void handleValidation_responseBody_containsRequiredFields() {
        MethodArgumentNotValidException ex = mock(MethodArgumentNotValidException.class);
        BindingResult bindingResult = mock(BindingResult.class);
        when(ex.getBindingResult()).thenReturn(bindingResult);
        when(bindingResult.getFieldErrors()).thenReturn(List.of());

        ResponseEntity<Map<String, Object>> response = handler.handleValidation(ex, request);

        assertThat(response.getBody()).containsKeys("timestamp", "status", "errorCode", "message", "fieldErrors");
        assertThat(response.getBody().get("errorCode")).isEqualTo("CUST_VALIDATION");
        assertThat(response.getBody().get("status")).isEqualTo(400);
    }

    // ── handleGeneral ─────────────────────────────────────────────────────────

    @Test
    void handleGeneral_unexpectedException_returns500WithErrorCode() {
        ResponseEntity<Map<String, Object>> response =
                handler.handleGeneral(new RuntimeException("boom"), request);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(response.getBody()).containsEntry("errorCode", "CUST_500");
        assertThat(response.getBody()).containsEntry("status", 500);
        assertThat(response.getBody()).containsKey("timestamp");
    }
}