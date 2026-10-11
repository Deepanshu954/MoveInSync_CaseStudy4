package com.moveinsync.settlement.audit.repository;

import com.moveinsync.settlement.audit.entity.AuditEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface AuditEventRepository extends JpaRepository<AuditEvent, Long> {
    List<AuditEvent> findByEntityTypeAndEntityId(String entityType, Long entityId);
    List<AuditEvent> findByCorrelationId(String correlationId);
}
