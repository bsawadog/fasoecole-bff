package org.afritechinnovations.controler.common;

import lombok.RequiredArgsConstructor;
import org.afritechinnovations.dto.common.OwnerDashboardDto;
import org.afritechinnovations.security.UserPrincipal;
import org.afritechinnovations.service.common.OwnerDashboardService;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/owner")
@RequiredArgsConstructor
public class OwnerDashboardController {

    private final OwnerDashboardService dashboardService;

    @GetMapping("/dashboard")
    public OwnerDashboardDto getDashboard(@RequestParam Long schoolId, Authentication authentication) {
        if (authentication == null || !(authentication.getPrincipal() instanceof UserPrincipal principal)) {
            throw new AccessDeniedException("Authentification requise");
        }
        if (!principal.getRoles().contains("SCHOOL_ADMIN") && !principal.getRoles().contains("STAFF")) {
            throw new AccessDeniedException("Accès réservé au propriétaire de l'établissement");
        }
        return dashboardService.getDashboard(schoolId, principal.getId());
    }
}
