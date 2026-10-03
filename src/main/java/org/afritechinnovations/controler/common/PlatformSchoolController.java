package org.afritechinnovations.controler.common;

import java.time.LocalDateTime;
import java.util.List;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import org.afritechinnovations.model.common.SchoolStatus;
import org.afritechinnovations.security.AccessGuard;
import org.afritechinnovations.service.common.SchoolService;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/platform/schools")
@RequiredArgsConstructor
public class PlatformSchoolController {
    private final AccessGuard guard;
    private final JdbcTemplate jdbc;
    private final SchoolService schools;

    public record SchoolRow(Long id, String name, String type, String status, Long ownerId,
                            String ownerName, String ownerEmail, String ownerPhone,
                            LocalDateTime activatedAt, LocalDateTime deactivatedAt, LocalDateTime submittedAt) { }
    public record SchoolPage(List<SchoolRow> items, long total, int page, int size) { }
    public record StatusRequest(@NotNull Boolean active) { }

    @GetMapping
    public SchoolPage list(@RequestParam(defaultValue="") String search,
                           @RequestParam(required=false) SchoolStatus status,
                           @RequestParam(defaultValue="0") int page,
                           @RequestParam(defaultValue="20") int size) {
        guard.requireSuperAdmin();
        if (page < 0 || size < 1 || size > 100 || search.length() > 200)
            throw new IllegalArgumentException("Paramètres de recherche invalides");
        String filter = " FROM schools s JOIN users u ON u.id=s.owner_id WHERE "
                + "(? = '' OR POSITION(LOWER(?) IN LOWER(CONCAT_WS(' ',s.name,u.first_name,u.last_name,u.email,u.phone))) > 0)"
                + " AND (? = '' OR s.status = ?)";
        String term=search.trim(), state=status == null ? "" : status.name();
        Long count=jdbc.queryForObject("SELECT COUNT(*)"+filter, Long.class,term,term,state,state);
        List<SchoolRow> items=jdbc.query("SELECT s.id,s.name,s.type,s.status,s.owner_id,"
                + "CONCAT_WS(' ',u.first_name,u.last_name) owner_name,u.email,u.phone,s.activated_at,s.deactivated_at,s.submitted_at"
                +filter+" ORDER BY LOWER(s.name),s.id LIMIT ? OFFSET ?", (r,n) -> new SchoolRow(
                r.getLong("id"),r.getString("name"),r.getString("type"),r.getString("status"),r.getLong("owner_id"),
                r.getString("owner_name"),r.getString("email"),r.getString("phone"),
                r.getObject("activated_at",LocalDateTime.class),r.getObject("deactivated_at",LocalDateTime.class),r.getObject("submitted_at",LocalDateTime.class)),
                term,term,state,state,size,(long)page*size);
        return new SchoolPage(items,count == null ? 0 : count,page,size);
    }

    @PatchMapping("/{id}/activation")
    public void activate(@PathVariable Long id,@Valid @RequestBody StatusRequest request) {
        guard.requireSuperAdmin();
        schools.updateStatus(id,request.active() ? SchoolStatus.ACTIVE : SchoolStatus.SUSPENDED);
    }
}
