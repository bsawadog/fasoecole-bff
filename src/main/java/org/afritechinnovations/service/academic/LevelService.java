package org.afritechinnovations.service.academic;

import lombok.RequiredArgsConstructor;
import org.afritechinnovations.dto.academic.LevelDto;
import org.afritechinnovations.model.academic.Level;
import org.afritechinnovations.model.common.School;
import org.afritechinnovations.repository.academic.LevelRepository;
import org.afritechinnovations.repository.common.SchoolRepository;
import org.afritechinnovations.model.common.StaffModule;
import org.afritechinnovations.security.SchoolPermissions;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional
public class LevelService {

    private final SchoolPermissions permissions;
    private final LevelRepository levelRepository;
    private final SchoolRepository schoolRepository;

    public List<LevelDto> findBySchool(Long schoolId) {
        return levelRepository.findBySchoolIdOrderByOrderIndexAsc(schoolId)
                .stream()
                .map(this::toDto)
                .toList();
    }

    public LevelDto findById(Long id) {
        Level level = levelRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Niveau introuvable: " + id));
        return toDto(level);
    }

    public LevelDto create(LevelDto dto, Long ownerId, boolean systemAdmin) {
        if (dto.getSchoolId() == null) {
            throw new IllegalArgumentException("L'établissement est obligatoire");
        }
        School school = schoolRepository.findById(dto.getSchoolId())
                .orElseThrow(() -> new IllegalArgumentException("Établissement introuvable: " + dto.getSchoolId()));
        requireOwner(school, ownerId, systemAdmin);
        Level level = Level.builder()
                .school(school)
                .name(dto.getName())
                .cycle(dto.getCycle())
                .orderIndex(dto.getOrderIndex() != null ? dto.getOrderIndex() : 0)
                .build();
        return toDto(levelRepository.save(level));
    }

    public LevelDto update(Long id, LevelDto dto, Long ownerId, boolean systemAdmin) {
        Level level = levelRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Niveau introuvable: " + id));
        requireOwner(level.getSchool(), ownerId, systemAdmin);
        if (!level.getSchool().getId().equals(dto.getSchoolId())) {
            throw new IllegalArgumentException("Ce niveau n'appartient pas à cet établissement");
        }
        level.setName(dto.getName());
        level.setCycle(dto.getCycle());
        level.setOrderIndex(dto.getOrderIndex());
        return toDto(levelRepository.save(level));
    }

    public void delete(Long id, Long ownerId, boolean systemAdmin) {
        Level level = levelRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Niveau introuvable: " + id));
        requireOwner(level.getSchool(), ownerId, systemAdmin);
        levelRepository.delete(level);
    }

    private void requireOwner(School school, Long ownerId, boolean systemAdmin) {
        if (!systemAdmin && !school.getOwner().getId().equals(ownerId)
                && !permissions.staffAllows(school.getId(), ownerId, StaffModule.MANAGEMENT)) {
            throw new AccessDeniedException("Vous ne pouvez gérer que les niveaux de votre établissement");
        }
    }

    private LevelDto toDto(Level level) {
        return LevelDto.builder()
                .id(level.getId())
                .schoolId(level.getSchool().getId())
                .name(level.getName())
                .cycle(level.getCycle())
                .orderIndex(level.getOrderIndex())
                .build();
    }
}
