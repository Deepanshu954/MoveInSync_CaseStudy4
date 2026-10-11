package com.moveinsync.settlement.common.exception;

import com.moveinsync.settlement.common.exception.dto.ExceptionResponseDto;
import com.moveinsync.settlement.common.exception.dto.ValidationExceptionResponseDto;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

/**
 * Centralized REST exception handler providing RFC 7807 compliant Problem Details responses.
 * Guarantees that client applications receive predictable, structured JSON error payloads
 * across all validation, domain, security, and runtime error conditions.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<ExceptionResponseDto> handleNotFound(
            ResourceNotFoundException ex,
            HttpServletRequest req
    ) {
        log.warn("Resource not found: {}", ex.getMessage());
        return buildErrorResponse(HttpStatus.NOT_FOUND, ex.getMessage(), req.getRequestURI());
    }

    @ExceptionHandler(DuplicateResourceException.class)
    public ResponseEntity<ExceptionResponseDto> handleDuplicate(
            DuplicateResourceException ex,
            HttpServletRequest req
    ) {
        log.warn("Duplicate resource conflict: {}", ex.getMessage());
        return buildErrorResponse(HttpStatus.CONFLICT, ex.getMessage(), req.getRequestURI());
    }

    @ExceptionHandler(IdempotencyConflictException.class)
    public ResponseEntity<ExceptionResponseDto> handleIdempotencyConflict(
            IdempotencyConflictException ex,
            HttpServletRequest req
    ) {
        log.warn("Idempotency conflict detected: {}", ex.getMessage());
        return buildErrorResponse(HttpStatus.CONFLICT, ex.getMessage(), req.getRequestURI());
    }

    @ExceptionHandler(InvalidStateTransitionException.class)
    public ResponseEntity<ExceptionResponseDto> handleInvalidState(
            InvalidStateTransitionException ex,
            HttpServletRequest req
    ) {
        log.warn("Invalid state transition attempted: {}", ex.getMessage());
        return buildErrorResponse(HttpStatus.UNPROCESSABLE_ENTITY, ex.getMessage(), req.getRequestURI());
    }

    @ExceptionHandler(PolicyViolationException.class)
    public ResponseEntity<ExceptionResponseDto> handlePolicyViolation(
            PolicyViolationException ex,
            HttpServletRequest req
    ) {
        log.warn("Business policy violation: {}", ex.getMessage());
        return buildErrorResponse(HttpStatus.UNPROCESSABLE_ENTITY, ex.getMessage(), req.getRequestURI());
    }

    @ExceptionHandler(BadCredentialsException.class)
    public ResponseEntity<ExceptionResponseDto> handleBadCredentials(
            BadCredentialsException ex,
            HttpServletRequest req
    ) {
        log.warn("Bad credentials attempt: {}", ex.getMessage());
        return buildErrorResponse(HttpStatus.UNAUTHORIZED, "Invalid username or password", req.getRequestURI());
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ExceptionResponseDto> handleAccessDenied(
            AccessDeniedException ex,
            HttpServletRequest req
    ) {
        log.warn("Access denied on {}: {}", req.getRequestURI(), ex.getMessage());
        return buildErrorResponse(HttpStatus.FORBIDDEN, "Access Denied: You do not possess the required permissions", req.getRequestURI());
    }

    /**
     * Handles Spring Bean Validation errors ({@code @Valid}), returning field-by-field error mappings.
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ValidationExceptionResponseDto> handleValidation(
            MethodArgumentNotValidException ex,
            HttpServletRequest req
    ) {
        Map<String, String> fieldErrors = new HashMap<>();
        for (FieldError fe : ex.getBindingResult().getFieldErrors()) {
            fieldErrors.put(fe.getField(), fe.getDefaultMessage());
        }

        log.warn("Validation failure on {}: {}", req.getRequestURI(), fieldErrors);
        ValidationExceptionResponseDto dto = new ValidationExceptionResponseDto(
                LocalDateTime.now(),
                HttpStatus.BAD_REQUEST.value(),
                "Bad Request",
                "Validation failed for one or more fields",
                req.getRequestURI(),
                fieldErrors
        );
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(dto);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ExceptionResponseDto> handleGeneric(
            Exception ex,
            HttpServletRequest req
    ) {
        log.error("Unhandled internal server error on {}: {}", req.getRequestURI(), ex.getMessage(), ex);
        return buildErrorResponse(HttpStatus.INTERNAL_SERVER_ERROR, "An unexpected internal server error occurred", req.getRequestURI());
    }

    private ResponseEntity<ExceptionResponseDto> buildErrorResponse(
            HttpStatus status,
            String message,
            String uri
    ) {
        ExceptionResponseDto dto = new ExceptionResponseDto(
                LocalDateTime.now(),
                status.value(),
                status.getReasonPhrase(),
                message,
                uri
        );
        return ResponseEntity.status(status).body(dto);
    }
}
