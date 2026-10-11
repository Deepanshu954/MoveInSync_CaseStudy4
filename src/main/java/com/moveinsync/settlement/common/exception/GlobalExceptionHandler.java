package com.moveinsync.settlement.common.exception;

import com.moveinsync.settlement.common.exception.dto.ExceptionResponseDto;
import com.moveinsync.settlement.common.exception.dto.ValidationExceptionResponseDto;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.validation.FieldError;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

/**
 * Centralized REST exception handler providing RFC 7807 compliant Problem Details responses.
 *
 * <p>Guarantees that client applications receive predictable, structured JSON error payloads
 * across all validation, domain, security, and runtime error conditions with correlation trace IDs.
 *
 * @author Deepanshu
 */
@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    // =========================================================================
    // 1. Client Errors & Resource Not Found (404, 400, 405)
    // =========================================================================

    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<ExceptionResponseDto> handleNotFound(ResourceNotFoundException ex, HttpServletRequest req) {
        log.warn("Resource not found on {}: {}", req.getRequestURI(), ex.getMessage());
        return buildErrorResponse(HttpStatus.NOT_FOUND, ex.getMessage(), req.getRequestURI());
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ExceptionResponseDto> handleNoResourceFound(NoResourceFoundException ex, HttpServletRequest req) {
        log.warn("Endpoint not found on {}: {}", req.getRequestURI(), ex.getMessage());
        return buildErrorResponse(HttpStatus.NOT_FOUND, "Endpoint not found: " + req.getRequestURI(), req.getRequestURI());
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ExceptionResponseDto> handleIllegalArgument(IllegalArgumentException ex, HttpServletRequest req) {
        log.warn("Illegal argument on {}: {}", req.getRequestURI(), ex.getMessage());
        return buildErrorResponse(HttpStatus.BAD_REQUEST, ex.getMessage(), req.getRequestURI());
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ExceptionResponseDto> handleTypeMismatch(MethodArgumentTypeMismatchException ex, HttpServletRequest req) {
        log.warn("Method argument type mismatch on {}: parameter '{}'", req.getRequestURI(), ex.getName());
        return buildErrorResponse(HttpStatus.BAD_REQUEST, "Invalid parameter type for: " + ex.getName(), req.getRequestURI());
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ExceptionResponseDto> handleNotReadable(HttpMessageNotReadableException ex, HttpServletRequest req) {
        log.warn("Malformed JSON payload on {}: {}", req.getRequestURI(), ex.getMessage());
        return buildErrorResponse(HttpStatus.BAD_REQUEST, "Malformed JSON request payload", req.getRequestURI());
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ExceptionResponseDto> handleMethodNotSupported(HttpRequestMethodNotSupportedException ex, HttpServletRequest req) {
        log.warn("HTTP method not supported on {}: {}", req.getRequestURI(), ex.getMessage());
        return buildErrorResponse(HttpStatus.METHOD_NOT_ALLOWED, ex.getMessage(), req.getRequestURI());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ValidationExceptionResponseDto> handleValidation(MethodArgumentNotValidException ex, HttpServletRequest req) {
        Map<String, String> fieldErrors = new HashMap<>();
        for (FieldError fe : ex.getBindingResult().getFieldErrors()) {
            fieldErrors.put(fe.getField(), fe.getDefaultMessage());
        }

        log.warn("Validation failure on {}: {}", req.getRequestURI(), fieldErrors);

        ValidationExceptionResponseDto dto = new ValidationExceptionResponseDto(
                LocalDateTime.now(),
                HttpStatus.BAD_REQUEST.value(),
                HttpStatus.BAD_REQUEST.getReasonPhrase(),
                "Validation failed for one or more fields",
                req.getRequestURI(),
                fieldErrors
        );
        dto.setTraceId(MDC.get("traceId"));

        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(dto);
    }

    // =========================================================================
    // 2. Conflict & Idempotency Errors (409)
    // =========================================================================

    @ExceptionHandler(DuplicateResourceException.class)
    public ResponseEntity<ExceptionResponseDto> handleDuplicate(DuplicateResourceException ex, HttpServletRequest req) {
        log.warn("Duplicate resource conflict on {}: {}", req.getRequestURI(), ex.getMessage());
        return buildErrorResponse(HttpStatus.CONFLICT, ex.getMessage(), req.getRequestURI());
    }

    @ExceptionHandler(IdempotencyConflictException.class)
    public ResponseEntity<ExceptionResponseDto> handleIdempotencyConflict(IdempotencyConflictException ex, HttpServletRequest req) {
        log.warn("Idempotency conflict detected on {}: {}", req.getRequestURI(), ex.getMessage());
        return buildErrorResponse(HttpStatus.CONFLICT, ex.getMessage(), req.getRequestURI());
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ExceptionResponseDto> handleDataIntegrityViolation(DataIntegrityViolationException ex, HttpServletRequest req) {
        log.warn("Database unique constraint collision on {}: {}", req.getRequestURI(), ex.getMessage());
        return buildErrorResponse(HttpStatus.CONFLICT, "Database constraint violation or concurrent duplicate request", req.getRequestURI());
    }

    // =========================================================================
    // 3. Domain Policy & State Transition Errors (422)
    // =========================================================================

    @ExceptionHandler(InvalidStateTransitionException.class)
    public ResponseEntity<ExceptionResponseDto> handleInvalidState(InvalidStateTransitionException ex, HttpServletRequest req) {
        log.warn("Invalid state transition on {}: {}", req.getRequestURI(), ex.getMessage());
        return buildErrorResponse(HttpStatus.UNPROCESSABLE_ENTITY, ex.getMessage(), req.getRequestURI());
    }

    @ExceptionHandler(PolicyViolationException.class)
    public ResponseEntity<ExceptionResponseDto> handlePolicyViolation(PolicyViolationException ex, HttpServletRequest req) {
        log.warn("Business policy violation on {}: {}", req.getRequestURI(), ex.getMessage());
        return buildErrorResponse(HttpStatus.UNPROCESSABLE_ENTITY, ex.getMessage(), req.getRequestURI());
    }

    // =========================================================================
    // 4. Security & Access Control Errors (401, 403)
    // =========================================================================

    @ExceptionHandler(BadCredentialsException.class)
    public ResponseEntity<ExceptionResponseDto> handleBadCredentials(BadCredentialsException ex, HttpServletRequest req) {
        log.warn("Bad credentials attempt on {}: {}", req.getRequestURI(), ex.getMessage());
        return buildErrorResponse(HttpStatus.UNAUTHORIZED, "Invalid username or password", req.getRequestURI());
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ExceptionResponseDto> handleAccessDenied(AccessDeniedException ex, HttpServletRequest req) {
        log.warn("Access denied on {}: {}", req.getRequestURI(), ex.getMessage());
        return buildErrorResponse(HttpStatus.FORBIDDEN, "Access Denied: You do not possess the required permissions", req.getRequestURI());
    }

    // =========================================================================
    // 5. Fallback Internal Server Errors (500)
    // =========================================================================

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ExceptionResponseDto> handleGeneric(Exception ex, HttpServletRequest req) {
        log.error("Unhandled internal server error on {}: {}", req.getRequestURI(), ex.getMessage(), ex);
        return buildErrorResponse(HttpStatus.INTERNAL_SERVER_ERROR, "An unexpected internal server error occurred", req.getRequestURI());
    }

    // =========================================================================
    // Helper Methods
    // =========================================================================

    private ResponseEntity<ExceptionResponseDto> buildErrorResponse(HttpStatus status, String message, String uri) {
        ExceptionResponseDto dto = ExceptionResponseDto.builder()
                .timestamp(LocalDateTime.now())
                .status(status.value())
                .error(status.getReasonPhrase())
                .message(message)
                .path(uri)
                .traceId(MDC.get("traceId"))
                .build();

        return ResponseEntity.status(status).body(dto);
    }
}
