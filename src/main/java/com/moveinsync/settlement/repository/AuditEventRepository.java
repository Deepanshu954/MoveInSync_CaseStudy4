package com.moveinsync.settlement.repository;

import com.moveinsync.settlement.entity.AuditEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;

/**
 * Repository for immutable regulatory compliance audit journals.
 *
 * @author Deepanshu
 */
@Repository
public interface AuditEventRepository extends JpaRepository<AuditEvent, Long> {
    List<AuditEvent> findByEntityTypeAndEntityId(String entityType, Long entityId);
    List<AuditEvent> findByCorrelationId(String correlationId);
}
