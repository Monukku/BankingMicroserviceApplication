package com.rewabank.cards.exception;

import org.junit.jupiter.api.Test;
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

    private WebRequest mockRequest(String uri) {
        WebRequest req = mock(WebRequest.class);
        when(req.getDescription(false)).thenReturn("uri=" + uri);
        return req;
    }

    // ── CardException ─────────────────────────────────────────────────────────

    @Test
    void handleCard_ShouldReturn404_ForCard001() {
        CardException ex = new CardException("CARD_001", "Card not found");
        ResponseEntity<Map<String, Object>> response =
                handler.handleCard(ex, mockRequest("/api/v1/cards/123"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(response.getBody()).containsEntry("errorCode", "CARD_001");
        assertThat(response.getBody()).containsEntry("message", "Card not found");
    }

    @Test
    void handleCard_ShouldReturn400_ForCard002() {
        CardException ex = new CardException("CARD_002", "Card already blocked");
        ResponseEntity<Map<String, Object>> response =
                handler.handleCard(ex, mockRequest("/api/v1/cards/123/block"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).containsEntry("errorCode", "CARD_002");
    }

    @Test
    void handleCard_ShouldReturn500_ForUnknownCode() {
        CardException ex = new CardException("CARD_999", "Unknown error");
        ResponseEntity<Map<String, Object>> response =
                handler.handleCard(ex, mockRequest("/api/v1/cards"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(response.getBody()).containsEntry("errorCode", "CARD_999");
    }

    // ── Validation ────────────────────────────────────────────────────────────

    @Test
    void handleValidation_ShouldReturn400_WithFieldErrors() {
        BeanPropertyBindingResult bindingResult =
                new BeanPropertyBindingResult(new Object(), "cardIssueRequest");
        bindingResult.addError(new FieldError(
                "cardIssueRequest", "cardType", "Card type is required"));

        MethodArgumentNotValidException ex =
                new MethodArgumentNotValidException(null, bindingResult);

        ResponseEntity<Map<String, Object>> response =
                handler.handleValidation(ex, mockRequest("/api/v1/cards"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).containsEntry("errorCode", "CARD_VALIDATION");
        @SuppressWarnings("unchecked")
        Map<String, String> fieldErrors =
                (Map<String, String>) response.getBody().get("fieldErrors");
        assertThat(fieldErrors).containsKey("cardType");
    }

    // ── Generic Exception ─────────────────────────────────────────────────────

    @Test
    void handleGeneral_ShouldReturn500() {
        Exception ex = new RuntimeException("Unexpected failure");
        ResponseEntity<Map<String, Object>> response =
                handler.handleGeneral(ex, mockRequest("/api/v1/cards"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(response.getBody()).containsEntry("errorCode", "CARD_500");
    }

    // ── Access Denied ─────────────────────────────────────────────────────────

    @Test
    void handleAccessDenied_ShouldReturn403() {
        AuthorizationDeniedException ex =
                new AuthorizationDeniedException("Access denied", () -> false);
        ResponseEntity<Map<String, Object>> response =
                handler.handleAccessDenied(ex, mockRequest("/api/v1/cards/123/block"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(response.getBody()).containsEntry("errorCode", "CARD_403");
    }
}