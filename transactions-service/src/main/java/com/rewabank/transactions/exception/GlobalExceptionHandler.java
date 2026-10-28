package com.rewabank.transactions.exception;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.authorization.AuthorizationDeniedException;
import org.springframework.validation.FieldError;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.stream.Collectors;

@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    @ExceptionHandler(TransactionException.class)
    public ResponseEntity<Map<String, Object>> handleTransaction(TransactionException ex, WebRequest request) {

        HttpStatus status = switch (ex.getErrorCode()) {
            case "TXN_007"      -> HttpStatus.NOT_FOUND;
            case "TXN_ACCT_005" -> HttpStatus.NOT_FOUND;
            case "TXN_001"      -> HttpStatus.CONFLICT;
            case "TXN_FRAUD_001",
                 "TXN_010"      -> HttpStatus.FORBIDDEN;
            case "TXN_ACCT_001",
                 "TXN_ACCT_002",
                 "TXN_ACCT_003",
                 "TXN_ACCT_004",
                 "TXN_ACCT_006" -> HttpStatus.SERVICE_UNAVAILABLE;
            case "TXN_011",
                 "TXN_012"      -> HttpStatus.UNPROCESSABLE_ENTITY;
            default             -> HttpStatus.BAD_REQUEST;
        };

        log.warn("TransactionException [{}]: {}",
                ex.getErrorCode(), ex.getMessage());

        return ResponseEntity.status(status).body(Map.of(
                "timestamp", LocalDateTime.now().toString(),
                "status",    status.value(),
                "errorCode", ex.getErrorCode(),
                "message",   ex.getMessage(),
                "path",      request.getDescription(false).replace("uri=", "")
        ));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, Object>> handleValidation(
            MethodArgumentNotValidException ex, WebRequest request) {

        Map<String, String> errors = ex.getBindingResult()
                .getFieldErrors().stream()
                .collect(Collectors.toMap(
                        FieldError::getField,
                        fe -> fe.getDefaultMessage() != null
                                ? fe.getDefaultMessage() : "Invalid",
                        (a, b) -> a));

        return ResponseEntity.badRequest().body(Map.of(
                "timestamp",   LocalDateTime.now().toString(),
                "status",      400,
                "errorCode",   "TXN_VALIDATION",
                "message",     "Validation failed",
                "fieldErrors", errors
        ));
    }

    // @PreAuthorize / method-security denials. With the servlet filter chain
    // disabled (addFilters=false) there is no ExceptionTranslationFilter to turn
    // this into a 403, so it must be mapped here — otherwise it falls through to 500.
    @ExceptionHandler(AuthorizationDeniedException.class)
    public ResponseEntity<Map<String, Object>> handleAuthorizationDenied(
            AuthorizationDeniedException ex, WebRequest request) {
        log.warn("Access denied: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of(
                "timestamp", LocalDateTime.now().toString(),
                "status",    HttpStatus.FORBIDDEN.value(),
                "errorCode", "TXN_FORBIDDEN",
                "message",   "Access denied"
        ));
    }

    // Missing required header (e.g. X-Idempotency-Key absent), malformed path
    // variable (e.g. non-UUID id), or unreadable body → 400, not 500.
    @ExceptionHandler({
            MissingRequestHeaderException.class,
            MethodArgumentTypeMismatchException.class,
            HttpMessageNotReadableException.class
    })
    public ResponseEntity<Map<String, Object>> handleBadRequest(
            Exception ex, WebRequest request) {
        log.warn("Bad request: {}", ex.getMessage());
        return ResponseEntity.badRequest().body(Map.of(
                "timestamp", LocalDateTime.now().toString(),
                "status",    HttpStatus.BAD_REQUEST.value(),
                "errorCode", "TXN_VALIDATION",
                "message",   "Invalid request"
        ));
    }

    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    public ResponseEntity<Map<String, Object>> handleUnsupportedMediaType(
            HttpMediaTypeNotSupportedException ex, WebRequest request) {
        log.warn("Unsupported media type: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.UNSUPPORTED_MEDIA_TYPE).body(Map.of(
                "timestamp", LocalDateTime.now().toString(),
                "status",    HttpStatus.UNSUPPORTED_MEDIA_TYPE.value(),
                "errorCode", "TXN_UNSUPPORTED_MEDIA_TYPE",
                "message",   "Unsupported media type"
        ));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> handleGeneral(
            Exception ex, WebRequest request) {
        log.error("Unexpected error: {}", ex.getMessage(), ex);
        return ResponseEntity.internalServerError().body(Map.of(
                "timestamp", LocalDateTime.now().toString(),
                "status",    500,
                "errorCode", "TXN_500",
                "message",   "An unexpected error occurred"
        ));
    }
}
