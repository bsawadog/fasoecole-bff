package org.afritechinnovations.service.common;

import lombok.RequiredArgsConstructor;
import org.afritechinnovations.dto.common.RoleDto;
import org.afritechinnovations.model.common.Role;
import org.afritechinnovations.model.common.RoleName;
import org.afritechinnovations.repository.common.RoleRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional
public class RoleService {

    private final RoleRepository roleRepository;

    public List<RoleDto> findAll() {
        return roleRepository.findAll()
                .stream()
                .map(this::toDto)
                .toList();
    }

    public RoleDto findById(Long id) {
        Role role = roleRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Rôle introuvable: " + id));
        return toDto(role);
    }

    public RoleDto findByName(RoleName name) {
        Role role = roleRepository.findByName(name)
                .orElseThrow(() -> new IllegalArgumentException("Rôle introuvable: " + name));
        return toDto(role);
    }

    public RoleDto create(RoleDto dto) {
        Role role = Role.builder()
                .name(dto.getName())
                .build();
        return toDto(roleRepository.save(role));
    }

    public void delete(Long id) {
        roleRepository.deleteById(id);
    }

    private RoleDto toDto(Role role) {
        return RoleDto.builder()
                .id(role.getId())
                .name(role.getName())
                .build();
    }
}
