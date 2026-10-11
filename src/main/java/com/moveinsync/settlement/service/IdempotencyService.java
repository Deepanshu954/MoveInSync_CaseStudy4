package com.moveinsync.settlement.service;

import com.moveinsync.settlement.common.exception.IdempotencyConflictException;
import com.moveinsync.settlement.entity.IdempotencyRecord;
import com.moveinsync.settlement.repository.IdempotencyRecordRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;

/**
 * Service managing Tier-1 HTTP Request Idempotency (FR-09).
 *
 * <p>Prevents duplicate execution by hashing incoming request payloads, detecting concurrent
 * execution requests with the same key, and caching completed responses for 24 hours.
 *
 * @author MoveInSync Core Settlement Team
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class IdempotencyService {

    
    private final IdempotencyRecordRepository repository;


    /**
     * Checks if a request with the given idempotency key has already been executed or is in progress.
     * If the key is new, an in-progress record is saved in a separate transaction.
     *
     * @param idempotencyKey unique key from HTTP header (e.g. UUID)
     * @param resourcePath   URI path of the resource being modified
     * @param requestPayload raw request body for tamper validation
     * @return {@link Optional} containing cached {@link IdempotencyRecord} if completed, or empty if newly started
     * @throws IdempotencyConflictException if concurrent in-flight request or payload hash mismatch
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Optional<IdempotencyRecord> checkOrStart(String idempotencyKey, String resourcePath, String requestPayload) {
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            return Optional.empty();
        }

        String requestHash = computeSha256(requestPayload);
        Optional<IdempotencyRecord> existingOpt = repository.findByIdempotencyKey(idempotencyKey);

        if (existingOpt.isPresent()) {
            IdempotencyRecord existing = existingOpt.get();

            // Tamper validation: ensure payload hash matches original request
            if (!existing.getRequestHash().equals(requestHash)) {
                log.warn("Idempotency key {} reused with different payload hash: {} vs {}",
                        idempotencyKey, existing.getRequestHash(), requestHash);
                throw new IdempotencyConflictException(
                        "Payload checksum mismatch: Idempotency-Key '" + idempotencyKey + "' was already used for a different request."
                );
            }

            // In-flight concurrency check
            if (existing.getStatus() == IdempotencyRecord.IdempotencyStatus.PROCESSING) {
                log.warn("Concurrent request detected for in-flight idempotency key {}", idempotencyKey);
                throw new IdempotencyConflictException(
                        "A request with Idempotency-Key '" + idempotencyKey + "' is currently being processed. Please retry shortly."
                );
            }

            // Completed request: return cached response
            if (existing.getStatus() == IdempotencyRecord.IdempotencyStatus.COMPLETED) {
                log.info("Returning cached response for idempotency key {}", idempotencyKey);
                return Optional.of(existing);
            }
        }

        // New key: register in PROCESSING state
        IdempotencyRecord newRecord = new IdempotencyRecord(
                null,
                idempotencyKey,
                resourcePath,
                requestHash,
                IdempotencyRecord.IdempotencyStatus.PROCESSING,
                null,
                null,
                Instant.now(),
                Instant.now().plus(24, ChronoUnit.HOURS)
        );
        repository.save(newRecord);
        log.debug("Registered new in-flight idempotency key {}", idempotencyKey);
        return Optional.empty();
    }

    /**
     * Marks an idempotency record as COMPLETED and caches the serialized HTTP response body.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void complete(String idempotencyKey, int responseStatus, String responsePayload) {
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            return;
        }

        repository.findByIdempotencyKey(idempotencyKey).ifPresent(record -> {
            record.setStatus(IdempotencyRecord.IdempotencyStatus.COMPLETED);
            record.setResponseStatus(responseStatus);
            record.setResponsePayload(responsePayload);
            repository.save(record);
            log.info("Completed idempotency record for key {}", idempotencyKey);
        });
    }

    /**
     * Marks an idempotency record as FAILED upon unhandled exception.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void fail(String idempotencyKey, String errorMessage) {
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            return;
        }

        repository.findByIdempotencyKey(idempotencyKey).ifPresent(record -> {
            record.setStatus(IdempotencyRecord.IdempotencyStatus.FAILED);
            record.setResponsePayload(errorMessage);
            repository.save(record);
            log.warn("Marked idempotency record as FAILED for key {}: {}", idempotencyKey, errorMessage);
        });
    }

    /**
     * Computes deterministic SHA-256 hex string of the given text payload.
     */
    public String computeSha256(String input) {
        if (input == null) {
            input = "";
        }
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder hexString = new StringBuilder();
            for (byte b : hash) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) {
                    hexString.append('0');
                }
                hexString.append(hex);
            }
            return hexString.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 algorithm not available in runtime", e);
        }
    }
}
