package org.afritechinnovations.controler.communication;

import lombok.RequiredArgsConstructor;
import org.afritechinnovations.dto.communication.AuditLogDto;
import org.afritechinnovations.security.AccessGuard;
import org.afritechinnovations.service.communication.AuditLogService;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/audit-logs")
@RequiredArgsConstructor
public class AuditLogController {

    private final AuditLogService auditLogService;
    private final AccessGuard guard;

    @GetMapping("/by-user/{userId}")
    public List<AuditLogDto> getByUser(@PathVariable Long userId) {
        guard.requireUserManager(userId);
        return auditLogService.findByUser(userId);
    }

    @GetMapping("/by-entity")
    public List<AuditLogDto> getByEntity(@RequestParam String entity, @RequestParam Long entityId) {
        guard.requireSuperAdmin();
        return auditLogService.findByEntity(entity, entityId);
    }

    /** Le journal est alimenté par le serveur ; seule la plateforme peut y ajouter une entrée manuelle. */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public AuditLogDto create(@RequestBody AuditLogDto dto) {
        guard.requireSuperAdmin();
        return auditLogService.create(dto);
    }
}
