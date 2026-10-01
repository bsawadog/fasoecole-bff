package org.afritechinnovations.controler.common;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.afritechinnovations.dto.common.CreateSchoolAccessRequest;
import org.afritechinnovations.dto.common.SchoolAccessRequestDto;
import org.afritechinnovations.security.AccessGuard;
import org.afritechinnovations.security.UserPrincipal;
import org.afritechinnovations.service.common.SchoolAccessRequestService;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequiredArgsConstructor
public class SchoolAccessRequestController {

    private final SchoolAccessRequestService service;
    private final AccessGuard guard;

    @GetMapping("/api/users/me/school-requests")
    public List<SchoolAccessRequestDto> mine() {
        return service.findMine(guard.currentUserId());
    }

    @PostMapping("/api/users/me/school-requests")
    @ResponseStatus(HttpStatus.CREATED)
    public SchoolAccessRequestDto create(@Valid @RequestBody CreateSchoolAccessRequest request) {
        return service.create(guard.currentUserId(), request);
    }

    @DeleteMapping("/api/users/me/school-requests/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void cancel(@PathVariable Long id) {
        service.cancel(guard.currentUserId(), id);
    }

    @GetMapping("/api/school-access-requests/pending")
    public List<SchoolAccessRequestDto> pending() {
        UserPrincipal approver = requireApprover();
        return service.findPendingFor(approver.getId(), isSystemAdmin(approver));
    }

    @PostMapping("/api/school-access-requests/{id}/approve")
    public SchoolAccessRequestDto approve(@PathVariable Long id) {
        UserPrincipal approver = requireApprover();
        return service.approve(id, approver.getId(), isSystemAdmin(approver));
    }

    @PostMapping("/api/school-access-requests/{id}/reject")
    public SchoolAccessRequestDto reject(@PathVariable Long id) {
        UserPrincipal approver = requireApprover();
        return service.reject(id, approver.getId(), isSystemAdmin(approver));
    }

    @PostMapping("/api/school-access-requests/{id}/confirm")
    public SchoolAccessRequestDto confirm(@PathVariable Long id) {
        UserPrincipal approver = requireApprover();
        return service.confirmAutomatic(id, approver.getId(), isSystemAdmin(approver));
    }

    @PostMapping("/api/school-access-requests/{id}/revoke")
    public SchoolAccessRequestDto revoke(@PathVariable Long id) {
        UserPrincipal approver = requireApprover();
        return service.revokeAutomatic(id, approver.getId(), isSystemAdmin(approver));
    }

    private UserPrincipal requireApprover() {
        UserPrincipal principal = guard.current();
        if (!isSystemAdmin(principal) && !principal.getRoles().contains("SCHOOL_ADMIN")) {
            throw new AccessDeniedException("Seul un propriétaire d'établissement peut traiter une demande d'accès");
        }
        return principal;
    }

    private static boolean isSystemAdmin(UserPrincipal principal) {
        return principal.getRoles().contains(AccessGuard.SUPER_ADMIN);
    }
}
