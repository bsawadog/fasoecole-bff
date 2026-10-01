package org.afritechinnovations.controler.people;

import lombok.RequiredArgsConstructor;
import org.afritechinnovations.dto.people.ParentDto;
import org.afritechinnovations.security.AccessGuard;
import org.afritechinnovations.service.people.ParentService;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/parents")
@RequiredArgsConstructor
public class ParentController {

    private final ParentService parentService;
    private final AccessGuard guard;

    @GetMapping("/{id}")
    public ParentDto getById(@PathVariable Long id) {
        ParentDto parent = parentService.findById(id);
        guard.requireUserManager(parent.getUserId());
        return parent;
    }

    @GetMapping("/by-user/{userId}")
    public ParentDto getByUserId(@PathVariable Long userId) {
        guard.requireUserManager(userId);
        return parentService.findByUserId(userId);
    }

    /** Les parents sont créés par le flux d'inscription ou la gestion des classes. */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ParentDto create(@RequestBody ParentDto dto) {
        guard.requireSuperAdmin();
        return parentService.create(dto);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id) {
        guard.requireSuperAdmin();
        parentService.delete(id);
    }
}
