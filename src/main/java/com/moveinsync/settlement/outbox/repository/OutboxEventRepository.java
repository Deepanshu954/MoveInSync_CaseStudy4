package com.moveinsync.settlement.outbox.repository;

import com.moveinsync.settlement.outbox.entity.OutboxEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface OutboxEventRepository extends JpaRepository<OutboxEvent, Long> {
    List<OutboxEvent> findTop50ByStatusOrderByCreatedAtAsc(OutboxEvent.OutboxStatus status);
}
