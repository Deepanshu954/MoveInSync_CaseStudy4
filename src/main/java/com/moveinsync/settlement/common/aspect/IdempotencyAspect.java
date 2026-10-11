package com.moveinsync.settlement.common.aspect;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.moveinsync.settlement.common.annotation.Idempotent;
import com.moveinsync.settlement.dto.SettlementRunResponseDto;
import com.moveinsync.settlement.entity.IdempotencyRecord;
import com.moveinsync.settlement.service.IdempotencyService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.Optional;

/**
 * Declarative AOP Aspect intercepting methods annotated with {@link Idempotent}.
 *
 * <p>Handles Tier-1 HTTP Request Idempotency transparently before the controller method executes.
 *
 * @author MoveInSync Core Settlement Team
 */
@Aspect
@Component
@Slf4j
@RequiredArgsConstructor
public class IdempotencyAspect {

    private final IdempotencyService idempotencyService;
    private final ObjectMapper objectMapper;

    @Around("@annotation(idempotent)")
    public Object handleIdempotency(ProceedingJoinPoint joinPoint, Idempotent idempotent) throws Throwable {
        ServletRequestAttributes attributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        if (attributes == null) {
            return joinPoint.proceed();
        }

        HttpServletRequest request = attributes.getRequest();
        String idempotencyKey = request.getHeader(idempotent.headerName());

        // If no idempotency key is passed, proceed normally
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            return joinPoint.proceed();
        }

        // Find request body argument (ignore String headers, Servlet objects, Authentication tokens)
        Object requestBody = null;
        for (Object arg : joinPoint.getArgs()) {
            if (arg != null 
                    && !(arg instanceof String) 
                    && !(arg instanceof HttpServletRequest) 
                    && !arg.getClass().getName().contains("Authentication") 
                    && !arg.getClass().getName().contains("Principal")) {
                requestBody = arg;
                break;
            }
        }

        String requestJson = requestBody != null ? objectMapper.writeValueAsString(requestBody) : "{}";

        // If previewOnly is true in the payload, skip HTTP idempotency caching
        if (idempotent.ignorePreview() && requestJson.contains("\"previewOnly\":true")) {
            return joinPoint.proceed();
        }

        String resourcePath = request.getRequestURI();

        // 1. Check or start in IdempotencyService
        Optional<IdempotencyRecord> cached = idempotencyService.checkOrStart(idempotencyKey, resourcePath, requestJson);
        if (cached.isPresent()) {
            IdempotencyRecord record = cached.get();
            log.info("IdempotencyAspect: Returning cached response for key {}", idempotencyKey);
            SettlementRunResponseDto cachedDto = objectMapper.readValue(record.getResponsePayload(), SettlementRunResponseDto.class);
            return ResponseEntity.status(record.getResponseStatus() != null ? record.getResponseStatus() : 200).body(cachedDto);
        }

        try {
            // 2. Execute target controller method
            Object result = joinPoint.proceed();

            // 3. Complete and cache response
            int status = 200;
            String responseJson;
            if (result instanceof ResponseEntity<?> responseEntity) {
                status = responseEntity.getStatusCode().value();
                responseJson = objectMapper.writeValueAsString(responseEntity.getBody());
            } else {
                responseJson = objectMapper.writeValueAsString(result);
            }

            idempotencyService.complete(idempotencyKey, status, responseJson);
            return result;
        } catch (Throwable ex) {
            idempotencyService.fail(idempotencyKey, ex.getMessage());
            throw ex;
        }
    }
}
