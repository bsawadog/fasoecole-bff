package org.afritechinnovations.controler.common;

import lombok.RequiredArgsConstructor;
import org.afritechinnovations.dto.common.RoleDto;
import org.afritechinnovations.model.common.RoleName;
import org.afritechinnovations.service.common.RoleService;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/roles")
@RequiredArgsConstructor
public class RoleController {

    private final RoleService roleService;

    @GetMapping
    public List<RoleDto> getAll() {
        return roleService.findAll();
    }

    @GetMapping("/{id}")
    public RoleDto getById(@PathVariable Long id) {
        return roleService.findById(id);
    }

    @GetMapping("/by-name/{name}")
    public RoleDto getByName(@PathVariable RoleName name) {
        return roleService.findByName(name);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public RoleDto create(@RequestBody RoleDto dto) {
        return roleService.create(dto);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id) {
        roleService.delete(id);
    }
}
