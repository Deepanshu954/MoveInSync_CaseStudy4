package com.moveinsync.settlement.service;

import com.moveinsync.settlement.common.exception.IdempotencyConflictException;
import com.moveinsync.settlement.entity.IdempotencyRecord;
import com.moveinsync.settlement.repository.IdempotencyRecordRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class IdempotencyServiceTest {

    @Mock
    private IdempotencyRecordRepository repository;

    private IdempotencyService service;

    @BeforeEach
    void setUp() {
        service = new IdempotencyService(repository);
    }

    @Test
    @DisplayName("Null or empty key returns empty without repository interaction")
    void shouldHandleNullOrEmptyKey() {
        assertThat(service.checkOrStart(null, "/api/test", "{}")).isEmpty();
        assertThat(service.checkOrStart("", "/api/test", "{}")).isEmpty();
        verifyNoInteractions(repository);
    }

    @Test
    @DisplayName("New key registers PROCESSING state and returns empty")
    void shouldRegisterNewKey() {
        when(repository.findByIdempotencyKey("KEY-123")).thenReturn(Optional.empty());

        Optional<IdempotencyRecord> result = service.checkOrStart("KEY-123", "/api/test", "{\"val\":1}");

        assertThat(result).isEmpty();
        verify(repository).save(any(IdempotencyRecord.class));
    }

    @Test
    @DisplayName("Completed key with identical payload returns cached record")
    void shouldReturnCachedCompletedRecord() {
        String payload = "{\"val\":1}";
        String hash = service.computeSha256(payload);

        IdempotencyRecord record = new IdempotencyRecord(
                1L, "KEY-123", "/api/test", hash,
                IdempotencyRecord.IdempotencyStatus.COMPLETED,
                200, "{\"status\":\"SUCCESS\"}",
                Instant.now(), Instant.now().plusSeconds(3600)
        );

        when(repository.findByIdempotencyKey("KEY-123")).thenReturn(Optional.of(record));

        Optional<IdempotencyRecord> result = service.checkOrStart("KEY-123", "/api/test", payload);

        assertThat(result).isPresent();
        assertThat(result.get().getResponsePayload()).isEqualTo("{\"status\":\"SUCCESS\"}");
        verify(repository, never()).save(any());
    }

    @Test
    @DisplayName("Reusing key with modified payload throws IdempotencyConflictException")
    void shouldThrowWhenPayloadHashDiffers() {
        String originalPayload = "{\"vendorId\":1}";
        String tamperedPayload = "{\"vendorId\":2}";
        String originalHash = service.computeSha256(originalPayload);

        IdempotencyRecord record = new IdempotencyRecord(
                1L, "KEY-123", "/api/test", originalHash,
                IdempotencyRecord.IdempotencyStatus.COMPLETED,
                200, "{}", Instant.now(), Instant.now().plusSeconds(3600)
        );

        when(repository.findByIdempotencyKey("KEY-123")).thenReturn(Optional.of(record));

        assertThatThrownBy(() -> service.checkOrStart("KEY-123", "/api/test", tamperedPayload))
                .isInstanceOf(IdempotencyConflictException.class)
                .hasMessageContaining("Payload checksum mismatch");
    }

    @Test
    @DisplayName("Concurrent request with key in PROCESSING throws IdempotencyConflictException")
    void shouldThrowWhenRequestIsConcurrentProcessing() {
        String payload = "{\"val\":1}";
        String hash = service.computeSha256(payload);

        IdempotencyRecord record = new IdempotencyRecord(
                1L, "KEY-123", "/api/test", hash,
                IdempotencyRecord.IdempotencyStatus.PROCESSING,
                null, null, Instant.now(), Instant.now().plusSeconds(3600)
        );

        when(repository.findByIdempotencyKey("KEY-123")).thenReturn(Optional.of(record));

        assertThatThrownBy(() -> service.checkOrStart("KEY-123", "/api/test", payload))
                .isInstanceOf(IdempotencyConflictException.class)
                .hasMessageContaining("currently being processed");
    }
}
