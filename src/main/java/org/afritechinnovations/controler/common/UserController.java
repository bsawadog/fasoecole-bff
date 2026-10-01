package org.afritechinnovations.controler.common;

import lombok.RequiredArgsConstructor;
import org.afritechinnovations.dto.common.ApproveUserRequest;
import org.afritechinnovations.dto.common.CreateUserRequest;
import org.afritechinnovations.dto.common.UserDto;
import org.afritechinnovations.security.AccessGuard;
import org.afritechinnovations.security.UserPrincipal;
import org.afritechinnovations.service.common.UserService;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import jakarta.validation.Valid;

import java.util.List;

@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;
    private final AccessGuard guard;

    @GetMapping("/me")
    public UserDto getCurrentUser(Authentication authentication) {
        if (authentication == null || !(authentication.getPrincipal() instanceof UserPrincipal principal)) {
            throw new AccessDeniedException("Authentification requise");
        }
        return userService.findById(principal.getId());
    }

    @GetMapping
    public List<UserDto> getActive() {
        guard.requireSuperAdmin();
        return userService.findActive();
    }

    @GetMapping("/pending")
    public List<UserDto> getPendingApprovals(Authentication authentication) {
        UserPrincipal approver = requireApprover(authentication);
        boolean systemAdmin = approver.getRoles().contains("SUPER_ADMIN");
        return userService.findPendingApprovals(approver.getId(), systemAdmin);
    }

    @PostMapping("/{id}/approve")
    public UserDto approve(@PathVariable Long id,
                           @Valid @RequestBody ApproveUserRequest request,
                           Authentication authentication) {
        UserPrincipal approver = requireApprover(authentication);
        boolean systemAdmin = approver.getRoles().contains("SUPER_ADMIN");
        return userService.approvePendingUser(
                id,
                request.getSchoolId(),
                request.getRole(),
                approver.getId(),
                systemAdmin
        );
    }

    @GetMapping("/{id}")
    public UserDto getById(@PathVariable Long id) {
        guard.requireUserManager(id);
        return userService.findById(id);
    }

    @GetMapping("/by-email")
    public UserDto getByEmail(@RequestParam String email) {
        guard.requireSuperAdmin();
        return userService.findByEmail(email);
    }

    /** Les comptes sont créés par l'inscription publique ; la création directe est réservée à la plateforme. */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public UserDto create(@RequestBody CreateUserRequest request) {
        guard.requireSuperAdmin();
        return userService.create(request);
    }

    @PutMapping("/{id}")
    public UserDto update(@PathVariable Long id, @RequestBody UserDto dto) {
        guard.requireUserManager(id);
        return userService.update(id, dto);
    }

    @PatchMapping("/{id}/deactivate")
    public void deactivate(@PathVariable Long id) {
        guard.requireSuperAdmin();
        userService.deactivate(id);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id) {
        guard.requireSuperAdmin();
        userService.delete(id);
    }

    private UserPrincipal requireApprover(Authentication authentication) {
        if (authentication == null || !(authentication.getPrincipal() instanceof UserPrincipal principal)) {
            throw new AccessDeniedException("Authentification requise");
        }
        if (!principal.getRoles().contains("SUPER_ADMIN") && !principal.getRoles().contains("SCHOOL_ADMIN")) {
            throw new AccessDeniedException("Seul un propriétaire d'établissement peut valider une demande");
        }
        return principal;
    }
}
