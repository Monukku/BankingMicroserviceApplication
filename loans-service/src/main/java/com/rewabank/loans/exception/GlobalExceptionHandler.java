package com.rewabank.loans.exception;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authorization.AuthorizationDeniedException;
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

    @ExceptionHandler(LoanException.class)
    public ResponseEntity<Map<String, Object>> handleLoan(
            LoanException ex, WebRequest request) {

        HttpStatus status = switch (ex.getErrorCode()) {
            case "LOAN_001"      -> HttpStatus.NOT_FOUND;
            case "LOAN_002",
                 "LOAN_003",
                 "LOAN_004",
                 "LOAN_005"      -> HttpStatus.BAD_REQUEST;
            case "LOAN_ACCT_001" -> HttpStatus.SERVICE_UNAVAILABLE;
            default              -> HttpStatus.INTERNAL_SERVER_ERROR;
        };

        log.warn("LoanException [{}]: {}", ex.getErrorCode(), ex.getMessage());

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
                "errorCode",   "LOAN_VALIDATION",
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
                "errorCode", "LOAN_500",
                "message",   "An unexpected error occurred"
        ));
    }

    @ExceptionHandler(AuthorizationDeniedException.class)
    public ResponseEntity<Map<String, Object>> handleAccessDenied(
            AuthorizationDeniedException ex, WebRequest request) {
        log.warn("Access denied: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of(
                "timestamp", LocalDateTime.now().toString(),
                "status",    403,
                "errorCode", "LOAN_403",
                "message",   "Access denied — insufficient role",
                "path",      request.getDescription(false).replace("uri=", "")
        ));
    }
}