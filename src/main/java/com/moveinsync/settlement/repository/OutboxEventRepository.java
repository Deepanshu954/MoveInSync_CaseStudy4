package com.moveinsync.settlement.repository;

import com.moveinsync.settlement.entity.OutboxEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
/**
 * Repository supporting Transactional Outbox pattern for guaranteed messaging.
 *
 * @author MoveInSync Core Settlement Team
 */
public interface OutboxEventRepository extends JpaRepository<OutboxEvent, Long> {
    List<OutboxEvent> findTop50ByStatusOrderByCreatedAtAsc(OutboxEvent.OutboxStatus status);
}
