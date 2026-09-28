package org.afritechinnovations.repository.communication;

import org.afritechinnovations.model.communication.AuditLog;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface AuditLogRepository extends JpaRepository<AuditLog, Long> {

    List<AuditLog> findByUserIdOrderByCreatedAtDesc(Long userId);

    List<AuditLog> findByEntityAndEntityId(String entity, Long entityId);
}