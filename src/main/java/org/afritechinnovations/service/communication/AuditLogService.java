package org.afritechinnovations.service.communication;

import lombok.RequiredArgsConstructor;
import org.afritechinnovations.dto.communication.AuditLogDto;
import org.afritechinnovations.model.common.User;
import org.afritechinnovations.model.communication.AuditLog;
import org.afritechinnovations.repository.communication.AuditLogRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional
public class AuditLogService {

    private final AuditLogRepository auditLogRepository;

    public List<AuditLogDto> findByUser(Long userId) {
        return auditLogRepository.findByUserIdOrderByCreatedAtDesc(userId)
                .stream()
                .map(this::toDto)
                .toList();
    }

    public List<AuditLogDto> findByEntity(String entity, Long entityId) {
        return auditLogRepository.findByEntityAndEntityId(entity, entityId)
                .stream()
                .map(this::toDto)
                .toList();
    }

    public AuditLogDto create(AuditLogDto dto) {
        AuditLog auditLog = AuditLog.builder()
                .user(dto.getUserId() != null ? User.builder().id(dto.getUserId()).build() : null)
                .action(dto.getAction())
                .entity(dto.getEntity())
                .entityId(dto.getEntityId())
                .build();
        return toDto(auditLogRepository.save(auditLog));
    }

    private AuditLogDto toDto(AuditLog auditLog) {
        return AuditLogDto.builder()
                .id(auditLog.getId())
                .userId(auditLog.getUser() != null ? auditLog.getUser().getId() : null)
                .action(auditLog.getAction())
                .entity(auditLog.getEntity())
                .entityId(auditLog.getEntityId())
                .createdAt(auditLog.getCreatedAt())
                .build();
    }
}
