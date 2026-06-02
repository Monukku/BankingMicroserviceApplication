package com.rewabank.transactions.exception;

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

    @ExceptionHandler(TransactionException.class)
    public ResponseEntity<Map<String, Object>> handleTransaction(
            TransactionException ex, WebRequest request) {

        HttpStatus status = switch (ex.getErrorCode()) {
            case "TXN_007"      -> HttpStatus.NOT_FOUND;
            case "TXN_001"      -> HttpStatus.CONFLICT;
            case "TXN_FRAUD_001"-> HttpStatus.FORBIDDEN;
            case "TXN_ACCT_001",
                 "TXN_ACCT_002" -> HttpStatus.SERVICE_UNAVAILABLE;
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
