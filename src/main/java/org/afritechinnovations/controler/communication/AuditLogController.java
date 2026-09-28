package org.afritechinnovations.controler.communication;

import lombok.RequiredArgsConstructor;
import org.afritechinnovations.dto.communication.AuditLogDto;
import org.afritechinnovations.service.communication.AuditLogService;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/audit-logs")
@RequiredArgsConstructor
public class AuditLogController {

    private final AuditLogService auditLogService;

    @GetMapping("/by-user/{userId}")
    public List<AuditLogDto> getByUser(@PathVariable Long userId) {
        return auditLogService.findByUser(userId);
    }

    @GetMapping("/by-entity")
    public List<AuditLogDto> getByEntity(@RequestParam String entity, @RequestParam Long entityId) {
        return auditLogService.findByEntity(entity, entityId);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public AuditLogDto create(@RequestBody AuditLogDto dto) {
        return auditLogService.create(dto);
    }
}
