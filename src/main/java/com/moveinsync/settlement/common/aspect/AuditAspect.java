package com.moveinsync.settlement.common.aspect;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.moveinsync.settlement.common.annotation.Audited;
import com.moveinsync.settlement.entity.AuditEvent;
import com.moveinsync.settlement.repository.AuditEventRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.UUID;

/**
 * Declarative AOP aspect intercepting methods annotated with {@link Audited}.
 *
 * <p>Automatically extracts the active caller, target entity ID, and persists an immutable
 * audit record in the database without modifying core business service methods.
 *
 * @author Deepanshu
 */
@Aspect
@Component
@Slf4j
@RequiredArgsConstructor
public class AuditAspect {

    private final AuditEventRepository auditEventRepository;
    private final ObjectMapper objectMapper;

    @Around("@annotation(audited)")
    public Object auditMethod(ProceedingJoinPoint joinPoint, Audited audited) throws Throwable {
        Object result = joinPoint.proceed();

        try {
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            String actor = (auth != null && auth.getName() != null) ? auth.getName() : "SYSTEM";

            Long entityId = null;
            // 1. Inspect method arguments for entity ID
            for (Object arg : joinPoint.getArgs()) {
                if (arg instanceof Long id) {
                    entityId = id;
                    break;
                }
            }

            // 2. Inspect return object for getId()
            if (entityId == null && result != null) {
                try {
                    var method = result.getClass().getMethod("getId");
                    Object val = method.invoke(result);
                    if (val instanceof Long id) {
                        entityId = id;
                    }
                } catch (Exception ignored) {}
            }

            String correlationId = UUID.randomUUID().toString();
            String detailsJson = null;
            try {
                detailsJson = objectMapper.writeValueAsString(result);
            } catch (Exception ignored) {}

            AuditEvent event = AuditEvent.builder()
                    .actor(actor)
                    .action(audited.action())
                    .entityType(audited.entityType())
                    .entityId(entityId)
                    .correlationId(correlationId)
                    .details(detailsJson)
                    .createdAt(Instant.now())
                    .build();

            auditEventRepository.save(event);
            log.info("Audited action '{}' on {}/{} by {}", audited.action(), audited.entityType(), entityId, actor);
        } catch (Exception ex) {
            log.warn("Failed to persist audit event for {}: {}", audited.action(), ex.getMessage());
        }

        return result;
    }
}
