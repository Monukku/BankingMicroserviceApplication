package com.rewabank.auth.exception;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.stream.Collectors;

@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    @ExceptionHandler(AuthException.class)
    public ResponseEntity<Map<String, Object>> handleAuthException(
            AuthException ex, WebRequest request) {

        HttpStatus status = switch (ex.getErrorCode()) {
            case "AUTH_001" -> HttpStatus.CONFLICT;
            case "AUTH_003" -> HttpStatus.NOT_FOUND;
            case "AUTH_004", "AUTH_005" -> HttpStatus.TOO_MANY_REQUESTS;
            case "AUTH_006", "AUTH_007" -> HttpStatus.BAD_REQUEST;
            default -> HttpStatus.INTERNAL_SERVER_ERROR;
        };

        log.warn("AuthException [{}]: {}", ex.getErrorCode(), ex.getMessage());

        return ResponseEntity.status(status).body(Map.of(
                "timestamp",     LocalDateTime.now().toString(),
                "status",        status.value(),
                "errorCode",     ex.getErrorCode(),
                "message",       ex.getMessage(),
                "path",          request.getDescription(false).replace("uri=", "")
        ));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, Object>> handleValidation(
            MethodArgumentNotValidException ex, WebRequest request) {

        Map<String, String> fieldErrors = ex.getBindingResult()
                .getFieldErrors().stream()
                .collect(Collectors.toMap(
                        FieldError::getField,
                        fe -> fe.getDefaultMessage() != null ? fe.getDefaultMessage() : "Invalid value",
                        (a, b) -> a
                ));

        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of(
                "timestamp",   LocalDateTime.now().toString(),
                "status",      400,
                "errorCode",   "AUTH_VALIDATION",
                "message",     "Validation failed",
                "fieldErrors", fieldErrors,
                "path",        request.getDescription(false).replace("uri=", "")
        ));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> handleGeneral(Exception ex, WebRequest request) {
        log.error("Unexpected error: {}", ex.getMessage(), ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of(
                "timestamp", LocalDateTime.now().toString(),
                "status",    500,
                "errorCode", "AUTH_500",
                "message",   "An unexpected error occurred"
        ));
    }
}
