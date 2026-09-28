package org.afritechinnovations.service.common;

import lombok.RequiredArgsConstructor;
import org.afritechinnovations.dto.common.SchoolUserDto;
import org.afritechinnovations.model.common.Role;
import org.afritechinnovations.model.common.School;
import org.afritechinnovations.model.common.SchoolUser;
import org.afritechinnovations.model.common.User;
import org.afritechinnovations.repository.common.SchoolUserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional
public class SchoolUserService {

    private final SchoolUserRepository schoolUserRepository;

    public List<SchoolUserDto> findBySchool(Long schoolId) {
        return schoolUserRepository.findAllWithUserAndRoleBySchoolId(schoolId)
                .stream()
                .map(this::toDto)
                .toList();
    }

    public List<SchoolUserDto> findByUser(Long userId) {
        return schoolUserRepository.findByUserId(userId)
                .stream()
                .map(this::toDto)
                .toList();
    }

    public SchoolUserDto findById(Long id) {
        SchoolUser schoolUser = schoolUserRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Affectation introuvable: " + id));
        return toDto(schoolUser);
    }

    public SchoolUserDto create(SchoolUserDto dto) {
        SchoolUser schoolUser = SchoolUser.builder()
                .user(User.builder().id(dto.getUserId()).build())
                .school(School.builder().id(dto.getSchoolId()).build())
                .role(Role.builder().id(dto.getRoleId()).build())
                .build();
        return toDto(schoolUserRepository.save(schoolUser));
    }

    public void delete(Long id) {
        schoolUserRepository.deleteById(id);
    }

    private SchoolUserDto toDto(SchoolUser schoolUser) {
        return SchoolUserDto.builder()
                .id(schoolUser.getId())
                .userId(schoolUser.getUser().getId())
                .schoolId(schoolUser.getSchool().getId())
                .roleId(schoolUser.getRole().getId())
                .build();
    }
}
