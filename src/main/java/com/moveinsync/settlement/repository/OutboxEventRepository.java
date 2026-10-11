package com.moveinsync.settlement.repository;

import com.moveinsync.settlement.entity.OutboxEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * Repository supporting Transactional Outbox pattern for guaranteed messaging.
 *
 * <p>Includes PostgreSQL {@code SKIP LOCKED} concurrency controls to safely coordinate
 * horizontally scaled Kubernetes worker pods without duplicate processing or thread deadlocks.
 *
 * @author MoveInSync Core Settlement Team
 */
@Repository
public interface OutboxEventRepository extends JpaRepository<OutboxEvent, Long> {

    @Query(value = """
        SELECT * FROM outbox_event
        WHERE status = 'PENDING'
        ORDER BY created_at ASC
        LIMIT :batchSize
        FOR UPDATE SKIP LOCKED
    """, nativeQuery = true)
    List<OutboxEvent> findPendingEventsForProcessing(@Param("batchSize") int batchSize);

    List<OutboxEvent> findTop50ByStatusOrderByCreatedAtAsc(OutboxEvent.OutboxStatus status);
    List<OutboxEvent> findByStatusOrderByCreatedAtAsc(OutboxEvent.OutboxStatus status);
    List<OutboxEvent> findByStatusOrderByCreatedAtDesc(OutboxEvent.OutboxStatus status);
    List<OutboxEvent> findByAggregateTypeAndAggregateId(String aggregateType, Long aggregateId);
    long countByStatus(OutboxEvent.OutboxStatus status);
}
