package com.moveinsync.settlement.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.Instant;

/**
 * Request idempotency cache record preventing duplicate execution of sensitive financial operations.
 *
 * <p>Stores request hashes and cached response bodies for 24 hours to handle network retry storms.
 *
 * @author Deepanshu
 */
@Entity
@Table(name = "idempotency_record")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
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
    @Builder.Default
    private Instant createdAt = Instant.now();

    @Column(name = "expires_at")
    private Instant expiresAt;

    public enum IdempotencyStatus {
        PROCESSING, COMPLETED, FAILED
    }
}
