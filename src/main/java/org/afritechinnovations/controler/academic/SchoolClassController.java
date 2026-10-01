package org.afritechinnovations.controler.academic;

import lombok.RequiredArgsConstructor;
import org.afritechinnovations.dto.academic.SchoolClassDto;
import org.afritechinnovations.service.academic.SchoolClassService;
import org.afritechinnovations.security.AccessGuard;
import org.afritechinnovations.security.UserPrincipal;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/classes")
@RequiredArgsConstructor
public class SchoolClassController {

    private final SchoolClassService schoolClassService;
    private final AccessGuard guard;

    @GetMapping
    public List<SchoolClassDto> getBySchool(@RequestParam Long schoolId) {
        guard.requireSchoolMember(schoolId);
        return schoolClassService.findBySchool(schoolId);
    }

    @GetMapping("/by-year/{academicYearId}")
    public List<SchoolClassDto> getByAcademicYear(@PathVariable Long academicYearId) {
        guard.requireSchoolMember(guard.schoolOfYear(academicYearId));
        return schoolClassService.findByAcademicYear(academicYearId);
    }

    @GetMapping("/with-level")
    public List<SchoolClassDto> getWithLevel(@RequestParam Long schoolId, @RequestParam Long academicYearId) {
        guard.requireSchoolMember(schoolId);
        return schoolClassService.findWithLevelBySchoolAndYear(schoolId, academicYearId);
    }

    @GetMapping("/{id}")
    public SchoolClassDto getById(@PathVariable Long id) {
        guard.requireSchoolMember(guard.schoolOfClass(id));
        return schoolClassService.findById(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public SchoolClassDto create(@RequestBody SchoolClassDto dto, Authentication authentication) {
        UserPrincipal principal = requireOwner(authentication);
        return schoolClassService.create(dto, principal.getId(), principal.getRoles().contains("SUPER_ADMIN"));
    }

    @PutMapping("/{id}")
    public SchoolClassDto update(@PathVariable Long id, @RequestBody SchoolClassDto dto, Authentication authentication) {
        UserPrincipal principal = requireOwner(authentication);
        return schoolClassService.update(id, dto, principal.getId(), principal.getRoles().contains("SUPER_ADMIN"));
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id, Authentication authentication) {
        UserPrincipal principal = requireOwner(authentication);
        schoolClassService.delete(id, principal.getId(), principal.getRoles().contains("SUPER_ADMIN"));
    }

    private UserPrincipal requireOwner(Authentication authentication) {
        if (authentication == null || !(authentication.getPrincipal() instanceof UserPrincipal principal)
                || (!principal.getRoles().contains("SCHOOL_ADMIN") && !principal.getRoles().contains("SUPER_ADMIN"))) {
            throw new AccessDeniedException("Accès réservé au propriétaire de l'établissement");
        }
        return principal;
    }
}
