package org.afritechinnovations.controler.common;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.afritechinnovations.dto.common.OwnerStaffDto;
import org.afritechinnovations.security.AccessGuard;
import org.afritechinnovations.service.common.OwnerStaffService;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** Personnel administratif et accès délégués (gestion réservée au propriétaire). */
@RestController
@RequestMapping("/api/owner/staff")
@RequiredArgsConstructor
public class OwnerStaffController {

    private final OwnerStaffService staffService;
    private final AccessGuard guard;

    /** Établissements et modules accessibles à l'utilisateur connecté dans l'espace propriétaire. */
    @GetMapping("/my-access")
    public List<OwnerStaffDto.SchoolAccess> myAccess() {
        return staffService.accessOf(guard.currentUserId());
    }

    @GetMapping("/schools/{schoolId}")
    public List<OwnerStaffDto.StaffRow> list(@PathVariable Long schoolId) {
        return staffService.list(schoolId, guard.currentUserId(), guard.isSuperAdmin());
    }

    @PostMapping("/schools/{schoolId}")
    @ResponseStatus(HttpStatus.CREATED)
    public OwnerStaffDto.StaffCreated create(@PathVariable Long schoolId,
                                             @Valid @RequestBody OwnerStaffDto.StaffRequest request) {
        return staffService.create(schoolId, request, guard.currentUserId(), guard.isSuperAdmin());
    }

    @PutMapping("/{staffId}")
    public OwnerStaffDto.StaffRow update(@PathVariable Long staffId,
                                         @Valid @RequestBody OwnerStaffDto.StaffRequest request) {
        return staffService.update(staffId, request, guard.currentUserId(), guard.isSuperAdmin());
    }

    @PostMapping("/{staffId}/suspend")
    public OwnerStaffDto.StaffRow suspend(@PathVariable Long staffId) {
        return staffService.setActive(staffId, false, guard.currentUserId(), guard.isSuperAdmin());
    }

    @PostMapping("/{staffId}/reactivate")
    public OwnerStaffDto.StaffRow reactivate(@PathVariable Long staffId) {
        return staffService.setActive(staffId, true, guard.currentUserId(), guard.isSuperAdmin());
    }

    @PostMapping("/{staffId}/reset-password")
    public OwnerStaffDto.PasswordReset resetPassword(@PathVariable Long staffId) {
        return staffService.resetPassword(staffId, guard.currentUserId(), guard.isSuperAdmin());
    }

    @DeleteMapping("/{staffId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void remove(@PathVariable Long staffId) {
        staffService.remove(staffId, guard.currentUserId(), guard.isSuperAdmin());
    }
}
