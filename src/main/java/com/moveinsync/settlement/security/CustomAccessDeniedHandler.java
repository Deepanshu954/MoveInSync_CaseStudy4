package com.moveinsync.settlement.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Access denial handler invoked when an authenticated user attempts to invoke an endpoint
 * for which they possess insufficient role privileges.
 *
 * <p>Returns HTTP 403 Forbidden with a structured RFC 7807 JSON error body.
 *
 * @author Deepanshu
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class CustomAccessDeniedHandler implements AccessDeniedHandler {

    private final ObjectMapper objectMapper;

    @Override
    public void handle(HttpServletRequest request, HttpServletResponse response, AccessDeniedException accessDeniedException) throws IOException {
        log.warn("Access denied on URI: {} - Reason: {}", request.getRequestURI(), accessDeniedException.getMessage());

        response.setStatus(HttpStatus.FORBIDDEN.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("timestamp", LocalDateTime.now().toString());
        body.put("status", HttpStatus.FORBIDDEN.value());
        body.put("error", HttpStatus.FORBIDDEN.getReasonPhrase());
        body.put("message", "Access Denied: Insufficient authority to perform this operation");
        body.put("path", request.getRequestURI());
        body.put("traceId", MDC.get("traceId"));

        objectMapper.writeValue(response.getOutputStream(), body);
    }
}
