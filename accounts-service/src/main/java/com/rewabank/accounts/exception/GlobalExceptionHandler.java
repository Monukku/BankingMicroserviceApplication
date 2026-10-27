package com.rewabank.accounts.exception;

import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.stream.Collectors;

@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    private static final String KEY_TIMESTAMP  = "timestamp";
    private static final String KEY_STATUS     = "status";
    private static final String KEY_ERROR_CODE = "errorCode";
    private static final String KEY_MESSAGE    = "message";
    private static final String KEY_PATH       = "path";

    @ExceptionHandler(AccountException.class)
    public ResponseEntity<Map<String, Object>> handleAccount(
            AccountException ex, WebRequest request) {

        HttpStatus status = switch (ex.getErrorCode()) {
            case "ACCT_002"                             -> HttpStatus.NOT_FOUND;
            case "ACCT_001", "ACCT_003", "ACCT_004",
                 "ACCT_005",  "ACCT_006", "ACCT_007",
                 "ACCT_008",  "ACCT_009",
                 "ACCT_FD_001", "ACCT_FD_002", "ACCT_FD_003",
                 "ACCT_FD_004", "ACCT_FD_005",
                 "ACCT_RD_001", "ACCT_RD_002", "ACCT_RD_003",
                 "ACCT_SAL_001", "ACCT_SAL_002"         -> HttpStatus.BAD_REQUEST;
            case "ACCT_KYC_001"                         -> HttpStatus.SERVICE_UNAVAILABLE;
            default                                     -> HttpStatus.INTERNAL_SERVER_ERROR;
        };

        log.warn("AccountException [{}]: {}", ex.getErrorCode(), ex.getMessage());

        return ResponseEntity.status(status).body(Map.of(
                KEY_TIMESTAMP, LocalDateTime.now(ZoneOffset.UTC).toString(),
                KEY_STATUS,    status.value(),
                KEY_ERROR_CODE, ex.getErrorCode(),
                KEY_MESSAGE,   ex.getMessage(),
                KEY_PATH,      request.getDescription(false).replace("uri=", "")
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
                KEY_TIMESTAMP,   LocalDateTime.now(ZoneOffset.UTC).toString(),
                KEY_STATUS,      400,
                KEY_ERROR_CODE,  "ACCT_VALIDATION",
                KEY_MESSAGE,     "Validation failed",
                "fieldErrors",   errors,
                KEY_PATH,        request.getDescription(false).replace("uri=", "")
        ));
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<Map<String, Object>> handleDataIntegrity(
            DataIntegrityViolationException ex, WebRequest request) {
        log.warn("DataIntegrityViolation: {}", ex.getMostSpecificCause().getMessage());
        return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of(
                KEY_TIMESTAMP, LocalDateTime.now(ZoneOffset.UTC).toString(),
                KEY_STATUS,    409,
                KEY_ERROR_CODE, "ACCT_CONFLICT",
                KEY_MESSAGE,   "Request conflicts with existing data",
                KEY_PATH,      request.getDescription(false).replace("uri=", "")
        ));
    }

    // A malformed path variable (e.g. a non-UUID {id}) is a client error — 400, not 500.
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<Map<String, Object>> handleTypeMismatch(
            MethodArgumentTypeMismatchException ex, WebRequest request) {
        return ResponseEntity.badRequest().body(Map.of(
                KEY_TIMESTAMP, LocalDateTime.now(ZoneOffset.UTC).toString(),
                KEY_STATUS,    400,
                KEY_ERROR_CODE, "ACCT_BAD_PARAM",
                KEY_MESSAGE,   "Invalid value for parameter: " + ex.getName(),
                KEY_PATH,      request.getDescription(false).replace("uri=", "")
        ));
    }

    // A missing required header (e.g. X-Customer-Id) is a client error — 400, not 500.
    @ExceptionHandler(MissingRequestHeaderException.class)
    public ResponseEntity<Map<String, Object>> handleMissingHeader(
            MissingRequestHeaderException ex, WebRequest request) {
        return ResponseEntity.badRequest().body(Map.of(
                KEY_TIMESTAMP, LocalDateTime.now(ZoneOffset.UTC).toString(),
                KEY_STATUS,    400,
                KEY_ERROR_CODE, "ACCT_HEADER_MISSING",
                KEY_MESSAGE,   "Required header missing: " + ex.getHeaderName(),
                KEY_PATH,      request.getDescription(false).replace("uri=", "")
        ));
    }

    // @PreAuthorize denials throw AuthorizationDeniedException (a subclass of
    // AccessDeniedException). Without this the catch-all below would turn a 403 into a 500.
    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<Map<String, Object>> handleAccessDenied(
            AccessDeniedException ex, WebRequest request) {
        log.warn("Access denied: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of(
                KEY_TIMESTAMP, LocalDateTime.now(ZoneOffset.UTC).toString(),
                KEY_STATUS,    403,
                KEY_ERROR_CODE, "ACCT_403",
                KEY_MESSAGE,   "Access denied",
                KEY_PATH,      request.getDescription(false).replace("uri=", "")
        ));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> handleGeneral(
            Exception ex, WebRequest request) {
        log.error("Unexpected error: {}", ex.getMessage(), ex);
        return ResponseEntity.internalServerError().body(Map.of(
                KEY_TIMESTAMP, LocalDateTime.now(ZoneOffset.UTC).toString(),
                KEY_STATUS,    500,
                KEY_ERROR_CODE, "ACCT_500",
                KEY_MESSAGE,   "An unexpected error occurred",
                KEY_PATH,      request.getDescription(false).replace("uri=", "")
        ));
    }
}
