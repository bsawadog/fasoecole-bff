package org.afritechinnovations.service.common;

import lombok.RequiredArgsConstructor;
import org.afritechinnovations.dto.common.SchoolDto;
import org.afritechinnovations.model.common.School;
import org.afritechinnovations.model.common.SchoolStatus;
import org.afritechinnovations.model.common.SchoolType;
import org.afritechinnovations.model.common.User;
import org.afritechinnovations.repository.common.SchoolRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional
public class SchoolService {

    private final SchoolRepository schoolRepository;

    public List<SchoolDto> findByStatus(SchoolStatus status) {
        return schoolRepository.findByStatus(status)
                .stream()
                .map(this::toDto)
                .toList();
    }

    public List<SchoolDto> findByType(SchoolType type) {
        return schoolRepository.findByType(type)
                .stream()
                .map(this::toDto)
                .toList();
    }

    public List<SchoolDto> findByOwner(Long ownerId) {
        return schoolRepository.findByOwnerId(ownerId)
                .stream()
                .map(this::toDto)
                .toList();
    }

    public SchoolDto findById(Long id) {
        School school = schoolRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("École introuvable: " + id));
        return toDto(school);
    }

    public SchoolDto create(SchoolDto dto) {
        School school = School.builder()
                .name(dto.getName())
                .type(dto.getType())
                .address(dto.getAddress())
                .phone(dto.getPhone())
                .email(dto.getEmail())
                .owner(User.builder().id(dto.getOwnerId()).build())
                .status(dto.getStatus() != null ? dto.getStatus() : SchoolStatus.ACTIVE)
                .build();
        return toDto(schoolRepository.save(school));
    }

    public SchoolDto update(Long id, SchoolDto dto) {
        School school = schoolRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("École introuvable: " + id));
        school.setName(dto.getName());
        school.setType(dto.getType());
        school.setAddress(dto.getAddress());
        school.setPhone(dto.getPhone());
        school.setEmail(dto.getEmail());
        return toDto(schoolRepository.save(school));
    }

    public SchoolDto updateStatus(Long id, SchoolStatus status) {
        School school = schoolRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("École introuvable: " + id));
        school.setStatus(status);
        return toDto(schoolRepository.save(school));
    }

    public SchoolDto finalizeCreation(Long id) {
        School school = schoolRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("École introuvable: " + id));
        if (school.getStatus() != SchoolStatus.DRAFT && school.getStatus() != SchoolStatus.ACTIVE) {
            throw new IllegalArgumentException("Seul un établissement en cours de création peut être finalisé");
        }
        school.setStatus(SchoolStatus.ACTIVE);
        return toDto(schoolRepository.save(school));
    }

    public void delete(Long id) {
        schoolRepository.deleteById(id);
    }

    private SchoolDto toDto(School school) {
        return SchoolDto.builder()
                .id(school.getId())
                .name(school.getName())
                .type(school.getType())
                .address(school.getAddress())
                .phone(school.getPhone())
                .email(school.getEmail())
                .ownerId(school.getOwner().getId())
                .status(school.getStatus())
                .build();
    }
}
