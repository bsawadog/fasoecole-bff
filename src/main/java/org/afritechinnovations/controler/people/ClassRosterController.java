package org.afritechinnovations.controler.people;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.afritechinnovations.dto.people.ClassRosterRowDto;
import org.afritechinnovations.dto.people.CreateRosterStudentRequest;
import org.afritechinnovations.dto.people.TransferStudentRequest;
import org.afritechinnovations.dto.people.UpdateParentProfileRequest;
import org.afritechinnovations.dto.people.UpdateStudentProfileRequest;
import org.afritechinnovations.security.UserPrincipal;
import org.afritechinnovations.service.people.ClassRosterService;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/classes/{classId}/roster")
@RequiredArgsConstructor
public class ClassRosterController {

    private final ClassRosterService classRosterService;

    @GetMapping
    public List<ClassRosterRowDto> getRoster(@PathVariable Long classId, Authentication authentication) {
        UserPrincipal principal = requireOwner(authentication);
        return classRosterService.getRoster(classId, principal.getId(), principal.getRoles().contains("SUPER_ADMIN"));
    }

    @PostMapping("/students")
    @ResponseStatus(HttpStatus.CREATED)
    public ClassRosterRowDto createStudent(@PathVariable Long classId,
                                            @Valid @RequestBody CreateRosterStudentRequest request,
                                            Authentication authentication) {
        UserPrincipal principal = requireOwner(authentication);
        return classRosterService.createStudent(classId, request, principal.getId(),
                principal.getRoles().contains("SUPER_ADMIN"));
    }

    @PutMapping("/students/{studentId}")
    public ClassRosterRowDto updateStudent(@PathVariable Long classId,
                                            @PathVariable Long studentId,
                                            @Valid @RequestBody UpdateStudentProfileRequest request,
                                            Authentication authentication) {
        UserPrincipal principal = requireOwner(authentication);
        return classRosterService.updateStudentProfile(classId, studentId, request, principal.getId(),
                principal.getRoles().contains("SUPER_ADMIN"));
    }

    @PutMapping("/parents/{parentId}")
    public ClassRosterRowDto updateParent(@PathVariable Long classId,
                                           @PathVariable Long parentId,
                                           @Valid @RequestBody UpdateParentProfileRequest request,
                                           Authentication authentication) {
        UserPrincipal principal = requireOwner(authentication);
        return classRosterService.updateParentProfile(classId, parentId, request, principal.getId(),
                principal.getRoles().contains("SUPER_ADMIN"));
    }

    @PostMapping("/students/{studentId}/transfer")
    public ClassRosterRowDto transferStudent(@PathVariable Long classId,
                                              @PathVariable Long studentId,
                                              @Valid @RequestBody TransferStudentRequest request,
                                              Authentication authentication) {
        UserPrincipal principal = requireOwner(authentication);
        return classRosterService.transferStudent(classId, studentId, request.getTargetClassId(),
                principal.getId(), principal.getRoles().contains("SUPER_ADMIN"));
    }

    @DeleteMapping("/students/{studentId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void removeStudent(@PathVariable Long classId,
                               @PathVariable Long studentId,
                               Authentication authentication) {
        UserPrincipal principal = requireOwner(authentication);
        classRosterService.removeStudentFromClass(classId, studentId, principal.getId(),
                principal.getRoles().contains("SUPER_ADMIN"));
    }

    private UserPrincipal requireOwner(Authentication authentication) {
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
