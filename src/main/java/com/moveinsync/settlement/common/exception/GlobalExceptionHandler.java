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

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<ExceptionResponseDto> handleNotFound(ResourceNotFoundException ex, HttpServletRequest request) {
        log.warn("Resource not found: {}", ex.getMessage());
        return buildResponse(HttpStatus.NOT_FOUND, ex.getMessage(), request.getRequestURI());
    }

    @ExceptionHandler({DuplicateResourceException.class, IdempotencyConflictException.class})
    public ResponseEntity<ExceptionResponseDto> handleConflict(RuntimeException ex, HttpServletRequest request) {
        log.warn("Conflict: {}", ex.getMessage());
        return buildResponse(HttpStatus.CONFLICT, ex.getMessage(), request.getRequestURI());
    }

    @ExceptionHandler({PolicyViolationException.class, InvalidStateTransitionException.class})
    public ResponseEntity<ExceptionResponseDto> handleUnprocessable(RuntimeException ex, HttpServletRequest request) {
        log.warn("Unprocessable entity: {}", ex.getMessage());
        return buildResponse(HttpStatus.UNPROCESSABLE_ENTITY, ex.getMessage(), request.getRequestURI());
    }

    @ExceptionHandler(BadCredentialsException.class)
    public ResponseEntity<ExceptionResponseDto> handleBadCredentials(BadCredentialsException ex, HttpServletRequest request) {
        log.warn("Bad credentials attempt: {}", ex.getMessage());
        return buildResponse(HttpStatus.UNAUTHORIZED, "Invalid username or password", request.getRequestURI());
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ExceptionResponseDto> handleAccessDenied(AccessDeniedException ex, HttpServletRequest request) {
        log.warn("Access denied on {}: {}", request.getRequestURI(), ex.getMessage());
        return buildResponse(HttpStatus.FORBIDDEN, "Access Denied: Insufficient permissions", request.getRequestURI());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ValidationExceptionResponseDto> handleValidation(MethodArgumentNotValidException ex, HttpServletRequest request) {
        Map<String, String> errors = new HashMap<>();
        for (FieldError fieldError : ex.getBindingResult().getFieldErrors()) {
            errors.put(fieldError.getField(), fieldError.getDefaultMessage());
        }
        log.warn("Validation failure on {}: {}", request.getRequestURI(), errors);

        ValidationExceptionResponseDto response = new ValidationExceptionResponseDto(
                LocalDateTime.now(),
                HttpStatus.BAD_REQUEST.value(),
                HttpStatus.BAD_REQUEST.getReasonPhrase(),
                "Validation failed for one or more fields",
                request.getRequestURI(),
                errors
        );
        return new ResponseEntity<>(response, HttpStatus.BAD_REQUEST);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ExceptionResponseDto> handleGeneral(Exception ex, HttpServletRequest request) {
        log.error("Unhandled server exception on {}: ", request.getRequestURI(), ex);
        return buildResponse(HttpStatus.INTERNAL_SERVER_ERROR, "An internal server error occurred", request.getRequestURI());
    }

    private ResponseEntity<ExceptionResponseDto> buildResponse(HttpStatus status, String message, String path) {
        ExceptionResponseDto dto = new ExceptionResponseDto(
                LocalDateTime.now(),
                status.value(),
                status.getReasonPhrase(),
                message,
                path
        );
        return new ResponseEntity<>(dto, status);
    }
}
