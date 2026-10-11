package com.moveinsync.settlement.common.exception;

public class IdempotencyConflictException extends RuntimeException {
    public IdempotencyConflictException(String message) { super(message); }
}
