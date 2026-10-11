package com.moveinsync.settlement.common.exception.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.*;

import java.time.LocalDateTime;

/**
 * Standardized RFC 7807 Problem Details response payload.
 *
 * <p>Carries structured error context, HTTP status codes, timestamp, URI,
 * and correlation trace token for distributed log tracing.
 *
 * @author Deepanshu
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ExceptionResponseDto {

    private LocalDateTime timestamp;
    private int status;
    private String error;
    private String message;
    private String path;
    private String traceId;

    public ExceptionResponseDto(
            LocalDateTime timestamp,
            int status,
            String error,
            String message,
            String path
    ) {
        this.timestamp = timestamp;
        this.status = status;
        this.error = error;
        this.message = message;
        this.path = path;
    }
}
