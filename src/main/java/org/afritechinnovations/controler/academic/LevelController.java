package org.afritechinnovations.controler.academic;

import lombok.RequiredArgsConstructor;
import org.afritechinnovations.dto.academic.LevelDto;
import org.afritechinnovations.service.academic.LevelService;
import org.afritechinnovations.security.AccessGuard;
import org.afritechinnovations.security.UserPrincipal;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/levels")
@RequiredArgsConstructor
public class LevelController {

    private final LevelService levelService;
    private final AccessGuard guard;

    @GetMapping
    public List<LevelDto> getBySchool(@RequestParam Long schoolId) {
        guard.requireSchoolMember(schoolId);
        return levelService.findBySchool(schoolId);
    }

    @GetMapping("/{id}")
    public LevelDto getById(@PathVariable Long id) {
        LevelDto level = levelService.findById(id);
        guard.requireSchoolMember(level.getSchoolId());
        return level;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public LevelDto create(@RequestBody LevelDto dto, Authentication authentication) {
        UserPrincipal principal = requireOwner(authentication);
        return levelService.create(dto, principal.getId(), principal.getRoles().contains("SUPER_ADMIN"));
    }

    @PutMapping("/{id}")
    public LevelDto update(@PathVariable Long id, @RequestBody LevelDto dto, Authentication authentication) {
        UserPrincipal principal = requireOwner(authentication);
        return levelService.update(id, dto, principal.getId(), principal.getRoles().contains("SUPER_ADMIN"));
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id, Authentication authentication) {
        UserPrincipal principal = requireOwner(authentication);
        levelService.delete(id, principal.getId(), principal.getRoles().contains("SUPER_ADMIN"));
    }

    private UserPrincipal requireOwner(Authentication authentication) {
        if (authentication == null || !(authentication.getPrincipal() instanceof UserPrincipal principal)
                || (!principal.getRoles().contains("SCHOOL_ADMIN") && !principal.getRoles().contains("SUPER_ADMIN"))) {
            throw new AccessDeniedException("Accès réservé au propriétaire de l'établissement");
        }
        return principal;
    }
}
