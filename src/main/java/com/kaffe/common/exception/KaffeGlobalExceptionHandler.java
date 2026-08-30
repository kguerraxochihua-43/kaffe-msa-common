package com.kaffe.common.exception;

import com.kaffe.common.web.ApiResponse;
import org.slf4j.MDC;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

@RestControllerAdvice
public class KaffeGlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(KaffeGlobalExceptionHandler.class);

    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<ApiResponse<Void>> handleNotFound(ResourceNotFoundException ex) {
        return error(HttpStatus.NOT_FOUND, ex.getMessage(), "RESOURCE_NOT_FOUND", ex, false);
    }

    @ExceptionHandler({DuplicateResourceException.class, ConflictException.class, DataIntegrityViolationException.class})
    public ResponseEntity<ApiResponse<Void>> handleConflict(RuntimeException ex) {
        return error(HttpStatus.CONFLICT, userMessage(ex, "Resource conflict"), "CONFLICT", ex, false);
    }

    @ExceptionHandler({BadRequestException.class, BusinessRuleException.class, IllegalArgumentException.class})
    public ResponseEntity<ApiResponse<Void>> handleBadRequest(RuntimeException ex) {
        return error(HttpStatus.BAD_REQUEST, ex.getMessage(), "BAD_REQUEST", ex, false);
    }

    @ExceptionHandler({UnauthorizedException.class, BadCredentialsException.class})
    public ResponseEntity<ApiResponse<Void>> handleUnauthorized(RuntimeException ex) {
        return error(HttpStatus.UNAUTHORIZED, ex.getMessage(), "UNAUTHORIZED", ex, false);
    }

    @ExceptionHandler({ForbiddenException.class, AccessDeniedException.class})
    public ResponseEntity<ApiResponse<Void>> handleForbidden(RuntimeException ex) {
        return error(HttpStatus.FORBIDDEN, ex.getMessage(), "FORBIDDEN", ex, false);
    }

    @ExceptionHandler(TooManyRequestsException.class)
    public ResponseEntity<ApiResponse<Void>> handleTooManyRequests(TooManyRequestsException ex) {
        return error(HttpStatus.TOO_MANY_REQUESTS, ex.getMessage(), "TOO_MANY_REQUESTS", ex, false);
    }

    @ExceptionHandler(ServiceUnavailableException.class)
    public ResponseEntity<ApiResponse<Void>> handleServiceUnavailable(ServiceUnavailableException ex) {
        return error(HttpStatus.SERVICE_UNAVAILABLE, ex.getMessage(), "SERVICE_UNAVAILABLE", ex, false);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Void>> handleValidation(MethodArgumentNotValidException ex) {
        Map<String, String> errors = new LinkedHashMap<>();
        ex.getBindingResult().getFieldErrors().forEach(error ->
                errors.put(error.getField(), error.getDefaultMessage())
        );
        ex.getBindingResult().getGlobalErrors().forEach(error ->
                errors.put(error.getObjectName(), error.getDefaultMessage())
        );

        String traceId = traceId();
        log.warn("Validation error [{}]: {}", traceId, errors);
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ApiResponse.validationError("Validation failed", errors, traceId));
    }

    @ExceptionHandler({
            HttpMessageNotReadableException.class,
            MissingServletRequestParameterException.class,
            MethodArgumentTypeMismatchException.class
    })
    public ResponseEntity<ApiResponse<Void>> handleInvalidRequest(Exception ex) {
        return error(HttpStatus.BAD_REQUEST, userMessage(ex, "Invalid request"), "BAD_REQUEST", ex, false);
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ApiResponse<Void>> handleMethodNotAllowed(HttpRequestMethodNotSupportedException ex) {
        return error(HttpStatus.METHOD_NOT_ALLOWED, userMessage(ex, "Method not allowed"), "METHOD_NOT_ALLOWED", ex, false);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleUnexpected(Exception ex) {
        return error(HttpStatus.INTERNAL_SERVER_ERROR, "Unexpected service error", "INTERNAL_ERROR", ex, true);
    }

    private ResponseEntity<ApiResponse<Void>> error(
            HttpStatus status,
            String message,
            String errorCode,
            Exception ex,
            boolean logStackTrace
    ) {
        String traceId = traceId();
        if (logStackTrace) {
            log.error("{} [{}]: {}", message, traceId, ex.getMessage(), ex);
        } else {
            log.warn("{} [{}]: {}", message, traceId, ex.getMessage());
        }
        return ResponseEntity.status(status).body(ApiResponse.error(message, errorCode, traceId));
    }

    private String userMessage(Exception ex, String fallback) {
        String message = ex.getMessage();
        if (message == null || message.isBlank()) {
            return fallback;
        }
        return message.lines().findFirst().orElse(fallback);
    }

    private String traceId() {
        String traceId = MDC.get("traceId");
        if (traceId != null && !traceId.isBlank()) {
            return traceId;
        }
        return UUID.randomUUID().toString();
    }
}
