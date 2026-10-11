package com.moveinsync.settlement.entity;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "idempotency_record")
/**
 * Request idempotency cache record preventing duplicate execution of sensitive financial operations.
 *
 * <p>Stores request hashes and cached response bodies for 24 hours to handle network retry storms.
 *
 * @author MoveInSync Core Settlement Team
 */
public class IdempotencyRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "idempotency_key", nullable = false, unique = true, length = 200)
    private String idempotencyKey;

    @Column(name = "resource_path", nullable = false, length = 200)
    private String resourcePath;

    @Column(name = "request_hash", nullable = false, length = 64)
    private String requestHash;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private IdempotencyStatus status;

    @Column(name = "response_status")
    private Integer responseStatus;

    @Column(name = "response_payload", columnDefinition = "TEXT")
    private String responsePayload;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "expires_at")
    private Instant expiresAt;

    public enum IdempotencyStatus {
        PROCESSING, COMPLETED, FAILED
    }

    public IdempotencyRecord() {}

    public IdempotencyRecord(Long id, String idempotencyKey, String resourcePath, String requestHash, IdempotencyStatus status, Integer responseStatus, String responsePayload, Instant createdAt, Instant expiresAt) {
        this.id = id;
        this.idempotencyKey = idempotencyKey;
        this.resourcePath = resourcePath;
        this.requestHash = requestHash;
        this.status = status;
        this.responseStatus = responseStatus;
        this.responsePayload = responsePayload;
        this.createdAt = createdAt;
        this.expiresAt = expiresAt;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getIdempotencyKey() {
        return idempotencyKey;
    }

    public void setIdempotencyKey(String idempotencyKey) {
        this.idempotencyKey = idempotencyKey;
    }

    public String getResourcePath() {
        return resourcePath;
    }

    public void setResourcePath(String resourcePath) {
        this.resourcePath = resourcePath;
    }

    public String getRequestHash() {
        return requestHash;
    }

    public void setRequestHash(String requestHash) {
        this.requestHash = requestHash;
    }

    public IdempotencyStatus getStatus() {
        return status;
    }

    public void setStatus(IdempotencyStatus status) {
        this.status = status;
    }

    public Integer getResponseStatus() {
        return responseStatus;
    }

    public void setResponseStatus(Integer responseStatus) {
        this.responseStatus = responseStatus;
    }

    public String getResponsePayload() {
        return responsePayload;
    }

    public void setResponsePayload(String responsePayload) {
        this.responsePayload = responsePayload;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }

    public void setExpiresAt(Instant expiresAt) {
        this.expiresAt = expiresAt;
    }
}
