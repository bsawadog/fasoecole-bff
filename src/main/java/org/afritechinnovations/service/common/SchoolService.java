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
    private final org.springframework.jdbc.core.JdbcTemplate jdbc;
    private final org.afritechinnovations.security.AccessGuard guard;

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
                .status(guard.isSuperAdmin() ? (dto.getStatus() != null ? dto.getStatus() : SchoolStatus.DRAFT) : SchoolStatus.DRAFT)
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
        guard.requireSuperAdmin();
        if (status == null) throw new IllegalArgumentException("Le statut est obligatoire");
        School school = schoolRepository.lockById(id)
                .orElseThrow(() -> new IllegalArgumentException("École introuvable: " + id));
        if (school.getStatus() == status) return toDto(school);
        if(status == SchoolStatus.ACTIVE && school.getStatus() == SchoolStatus.DRAFT)
            throw new IllegalArgumentException("Le propriétaire doit soumettre la création avant son activation");
        if(status == SchoolStatus.DRAFT || status == SchoolStatus.PENDING_APPROVAL)
            throw new IllegalArgumentException("Utilisez l’assistant de création pour préparer et soumettre l’établissement");
        jdbc.update("INSERT INTO school_status_events(school_id,actor_id,previous_status,new_status) VALUES(?,?,?,?)",
                id, guard.currentUserId(), school.getStatus().name(), status.name());
        school.setStatus(status);
        if (status == SchoolStatus.ACTIVE) school.setActivatedAt(java.time.LocalDateTime.now());
        else school.setDeactivatedAt(java.time.LocalDateTime.now());
        return toDto(schoolRepository.save(school));
    }

    public SchoolDto finalizeCreation(Long id) {
        guard.requireOwnedSchool(id);
        School school = schoolRepository.lockById(id)
                .orElseThrow(() -> new IllegalArgumentException("École introuvable: " + id));
        if(school.getStatus() == SchoolStatus.PENDING_APPROVAL || school.getStatus() == SchoolStatus.ACTIVE) return toDto(school);
        if (school.getStatus() != SchoolStatus.DRAFT && school.getStatus() != SchoolStatus.ACTIVE) {
            throw new IllegalArgumentException("Seul un établissement en cours de création peut être finalisé");
        }
        if(Boolean.FALSE.equals(jdbc.queryForObject("SELECT EXISTS(SELECT 1 FROM academic_years WHERE school_id=?) AND EXISTS(SELECT 1 FROM classes WHERE school_id=?)",Boolean.class,id,id)))
            throw new IllegalArgumentException("Ajoutez au moins une année scolaire et une classe avant de soumettre l’établissement");
        jdbc.update("INSERT INTO school_status_events(school_id,actor_id,previous_status,new_status) VALUES(?,?,?,?)",
                id,guard.currentUserId(),SchoolStatus.DRAFT.name(),SchoolStatus.PENDING_APPROVAL.name());
        school.setStatus(SchoolStatus.PENDING_APPROVAL);
        school.setSubmittedAt(java.time.LocalDateTime.now());
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
                .submittedAt(school.getSubmittedAt())
                .build();
    }
}
