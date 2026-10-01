package org.afritechinnovations.controler.people;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.afritechinnovations.dto.people.NewStudentEnrollmentRequest;
import org.afritechinnovations.dto.people.OwnerEnrollmentDto;
import org.afritechinnovations.security.UserPrincipal;
import org.afritechinnovations.service.people.OwnerEnrollmentService;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/owner/enrollment")
@RequiredArgsConstructor
public class OwnerEnrollmentController {

    private final OwnerEnrollmentService enrollmentService;

    @GetMapping("/schools/{schoolId}/overview")
    public OwnerEnrollmentDto.Overview overview(@PathVariable Long schoolId, Authentication authentication) {
        UserPrincipal p = requireOwner(authentication);
        return enrollmentService.overview(schoolId, p.getId(), isSuperAdmin(p));
    }

    @PostMapping("/schools/{schoolId}/years")
    @ResponseStatus(HttpStatus.CREATED)
    public OwnerEnrollmentDto.NewYearResult createYear(@PathVariable Long schoolId,
                                                       @Valid @RequestBody OwnerEnrollmentDto.NewYearRequest request,
                                                       Authentication authentication) {
        UserPrincipal p = requireOwner(authentication);
        return enrollmentService.createYear(schoolId, request, p.getId(), isSuperAdmin(p));
    }

    @GetMapping("/schools/{schoolId}/years/{yearId}/classes")
    public List<OwnerEnrollmentDto.TargetClass> yearClasses(@PathVariable Long schoolId, @PathVariable Long yearId,
                                                           Authentication authentication) {
        UserPrincipal p = requireOwner(authentication);
        return enrollmentService.yearClasses(schoolId, yearId, p.getId(), isSuperAdmin(p));
    }

    @PostMapping("/schools/{schoolId}/students")
    @ResponseStatus(HttpStatus.CREATED)
    public OwnerEnrollmentDto.RegistrationResult registerStudent(@PathVariable Long schoolId,
                                             @Valid @RequestBody NewStudentEnrollmentRequest request,
                                             Authentication authentication) {
        UserPrincipal p = requireOwner(authentication);
        return enrollmentService.registerStudent(schoolId, request, p.getId(), isSuperAdmin(p));
    }

    @GetMapping("/schools/{schoolId}/guardians")
    public List<OwnerEnrollmentDto.GuardianOption> searchGuardians(@PathVariable Long schoolId,
                                                                   @RequestParam(name = "q", required = false) String query,
                                                                   Authentication authentication) {
        UserPrincipal p = requireOwner(authentication);
        return enrollmentService.searchGuardians(schoolId, query, p.getId(), isSuperAdmin(p));
    }

    @GetMapping("/schools/{schoolId}/fees")
    public List<OwnerEnrollmentDto.EnrollmentFee> enrollmentFees(@PathVariable Long schoolId,
                                                                 Authentication authentication) {
        UserPrincipal p = requireOwner(authentication);
        return enrollmentService.enrollmentFees(schoolId, p.getId(), isSuperAdmin(p));
    }

    @PostMapping("/years/{yearId}/current")
    public OwnerEnrollmentDto.Overview setCurrent(@PathVariable Long yearId, Authentication authentication) {
        UserPrincipal p = requireOwner(authentication);
        return enrollmentService.setCurrent(yearId, p.getId(), isSuperAdmin(p));
    }

    @GetMapping("/schools/{schoolId}/promotion")
    public OwnerEnrollmentDto.PromotionPlan plan(@PathVariable Long schoolId,
                                                 @RequestParam Long fromYearId,
                                                 @RequestParam Long toYearId,
                                                 Authentication authentication) {
        UserPrincipal p = requireOwner(authentication);
        return enrollmentService.plan(schoolId, fromYearId, toYearId, p.getId(), isSuperAdmin(p));
    }

    @PostMapping("/schools/{schoolId}/promotion")
    public OwnerEnrollmentDto.PromotionResult apply(@PathVariable Long schoolId,
                                                    @Valid @RequestBody OwnerEnrollmentDto.PromotionRequest request,
                                                    Authentication authentication) {
        UserPrincipal p = requireOwner(authentication);
        return enrollmentService.apply(schoolId, request, p.getId(), isSuperAdmin(p));
    }

    @PostMapping("/enrollments/{enrollmentId}/undo")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void undo(@PathVariable Long enrollmentId, Authentication authentication) {
        UserPrincipal p = requireOwner(authentication);
        enrollmentService.undo(enrollmentId, p.getId(), isSuperAdmin(p));
    }

    private static boolean isSuperAdmin(UserPrincipal principal) {
        return principal.getRoles().contains("SUPER_ADMIN");
    }

    private static UserPrincipal requireOwner(Authentication authentication) {
        if (authentication == null || !(authentication.getPrincipal() instanceof UserPrincipal principal)) {
            throw new AccessDeniedException("Authentification requise");
        }
        if (!principal.getRoles().contains("SCHOOL_ADMIN") && !principal.getRoles().contains("STAFF")
                && !principal.getRoles().contains("SUPER_ADMIN")) {
            throw new AccessDeniedException("Accès réservé au propriétaire de l'établissement");
        }
        return principal;
    }
}
