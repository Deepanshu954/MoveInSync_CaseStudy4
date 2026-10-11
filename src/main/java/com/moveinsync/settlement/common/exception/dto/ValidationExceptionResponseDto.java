package com.moveinsync.settlement.common.exception.dto;

import java.time.LocalDateTime;
import java.util.Map;

public class ValidationExceptionResponseDto extends ExceptionResponseDto {
    private Map<String, String> fieldErrors;

    public ValidationExceptionResponseDto() {}

    public ValidationExceptionResponseDto(LocalDateTime timestamp, int status, String error, String message, String path, Map<String, String> fieldErrors) {
        super(timestamp, status, error, message, path);
        this.fieldErrors = fieldErrors;
    }

    public Map<String, String> getFieldErrors() { return fieldErrors; }
    public void setFieldErrors(Map<String, String> fieldErrors) { this.fieldErrors = fieldErrors; }
}
