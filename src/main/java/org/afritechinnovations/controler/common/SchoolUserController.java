package org.afritechinnovations.controler.common;

import lombok.RequiredArgsConstructor;
import org.afritechinnovations.dto.common.SchoolUserDto;
import org.afritechinnovations.security.AccessGuard;
import org.afritechinnovations.service.common.RoleService;
import org.afritechinnovations.service.common.SchoolUserService;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/school-users")
@RequiredArgsConstructor
public class SchoolUserController {

    private final SchoolUserService schoolUserService;
    private final RoleService roleService;
    private final AccessGuard guard;

    @GetMapping("/by-school/{schoolId}")
    public List<SchoolUserDto> getBySchool(@PathVariable Long schoolId) {
        guard.requireOwnedSchool(schoolId);
        return schoolUserService.findBySchool(schoolId);
    }

    @GetMapping("/by-user/{userId}")
    public List<SchoolUserDto> getByUser(@PathVariable Long userId) {
        guard.requireSelfOrSuperAdmin(userId);
        return schoolUserService.findByUser(userId);
    }

    @GetMapping("/{id}")
    public SchoolUserDto getById(@PathVariable Long id) {
        SchoolUserDto link = schoolUserService.findById(id);
        if (!guard.currentUserId().equals(link.getUserId())) {
            guard.requireOwnedSchool(link.getSchoolId());
        }
        return link;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public SchoolUserDto create(@RequestBody SchoolUserDto dto) {
        guard.requireOwnedSchool(dto.getSchoolId());
        if (dto.getRoleId() == null) {
            throw new IllegalArgumentException("Le rôle est obligatoire");
        }
        String role = roleService.findById(dto.getRoleId()).getName();
        if ((AccessGuard.SUPER_ADMIN.equals(role) || "SCHOOL_ADMIN".equals(role)) && !guard.isSuperAdmin()) {
            throw new AccessDeniedException("Vous ne pouvez pas attribuer le rôle d'administrateur de la plateforme");
        }
        return schoolUserService.create(dto);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id) {
        guard.requireOwnedSchool(schoolUserService.findById(id).getSchoolId());
        schoolUserService.delete(id);
    }
}
