package com.moveinsync.settlement.repository;

import com.moveinsync.settlement.entity.IdempotencyRecord;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.Optional;

@Repository
/**
 * Repository for tracking API mutation idempotency tokens.
 *
 * @author MoveInSync Core Settlement Team
 */
public interface IdempotencyRecordRepository extends JpaRepository<IdempotencyRecord, Long> {
    Optional<IdempotencyRecord> findByIdempotencyKey(String idempotencyKey);
}
