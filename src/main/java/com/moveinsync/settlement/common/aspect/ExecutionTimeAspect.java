package com.moveinsync.settlement.common.aspect;

import com.moveinsync.settlement.common.annotation.ExecutionTime;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.stereotype.Component;

/**
 * Aspect logging precise execution latency for performance-critical calculation methods.
 *
 * @author Deepanshu
 */
@Aspect
@Component
@Slf4j
public class ExecutionTimeAspect {

    @Around("@annotation(executionTime)")
    public Object profileMethod(ProceedingJoinPoint joinPoint, ExecutionTime executionTime) throws Throwable {
        long start = System.currentTimeMillis();
        try {
            return joinPoint.proceed();
        } finally {
            long duration = System.currentTimeMillis() - start;
            String operation = !executionTime.value().isBlank()
                    ? executionTime.value()
                    : joinPoint.getSignature().toShortString();
            log.info("[PERF] {} executed in {} ms", operation, duration);
        }
    }
}
