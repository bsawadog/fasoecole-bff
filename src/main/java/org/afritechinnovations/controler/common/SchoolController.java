package org.afritechinnovations.controler.common;

import lombok.RequiredArgsConstructor;
import org.afritechinnovations.dto.common.SchoolDto;
import org.afritechinnovations.dto.common.RegistrationSchoolDto;
import org.afritechinnovations.model.common.SchoolStatus;
import org.afritechinnovations.model.common.SchoolType;
import org.afritechinnovations.service.common.SchoolService;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.afritechinnovations.security.UserPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/schools")
@RequiredArgsConstructor
public class SchoolController {

    private final SchoolService schoolService;

    @GetMapping("/registration-options")
    public List<RegistrationSchoolDto> getRegistrationOptions() {
        return schoolService.findByStatus(SchoolStatus.ACTIVE).stream()
                .map(school -> new RegistrationSchoolDto(school.getId(), school.getName(), school.getType()))
                .toList();
    }

    @GetMapping
    public List<SchoolDto> getByStatus(@RequestParam(required = false) SchoolStatus status,
                                        @RequestParam(required = false) SchoolType type) {
        if (status != null) {
            return schoolService.findByStatus(status);
        }
        if (type != null) {
            return schoolService.findByType(type);
        }
        return schoolService.findByStatus(SchoolStatus.ACTIVE);
    }

    @GetMapping("/by-owner/{ownerId}")
    public List<SchoolDto> getByOwner(@PathVariable Long ownerId, Authentication authentication) {
        if (authentication == null || !(authentication.getPrincipal() instanceof UserPrincipal principal)) {
            throw new AccessDeniedException("Authentification requise");
        }
        if (!principal.getId().equals(ownerId) && !principal.getRoles().contains("SUPER_ADMIN")) {
            throw new AccessDeniedException("Vous ne pouvez consulter que vos propres établissements");
        }
        return schoolService.findByOwner(ownerId);
    }

    @GetMapping("/{id}")
    public SchoolDto getById(@PathVariable Long id) {
        return schoolService.findById(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public SchoolDto create(@RequestBody SchoolDto dto) {
        return schoolService.create(dto);
    }

    @PutMapping("/{id}")
    public SchoolDto update(@PathVariable Long id, @RequestBody SchoolDto dto) {
        return schoolService.update(id, dto);
    }

    @PatchMapping("/{id}/status")
    public SchoolDto updateStatus(@PathVariable Long id, @RequestParam SchoolStatus status) {
        return schoolService.updateStatus(id, status);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id) {
        schoolService.delete(id);
    }
}
