package org.afritechinnovations.service.academic;

import lombok.RequiredArgsConstructor;
import org.afritechinnovations.dto.academic.LevelDto;
import org.afritechinnovations.model.academic.Level;
import org.afritechinnovations.model.common.School;
import org.afritechinnovations.repository.academic.LevelRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional
public class LevelService {

    private final LevelRepository levelRepository;

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

    public LevelDto create(LevelDto dto) {
        Level level = Level.builder()
                .school(School.builder().id(dto.getSchoolId()).build())
                .name(dto.getName())
                .cycle(dto.getCycle())
                .orderIndex(dto.getOrderIndex() != null ? dto.getOrderIndex() : 0)
                .build();
        return toDto(levelRepository.save(level));
    }

    public LevelDto update(Long id, LevelDto dto) {
        Level level = levelRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Niveau introuvable: " + id));
        level.setName(dto.getName());
        level.setCycle(dto.getCycle());
        level.setOrderIndex(dto.getOrderIndex());
        return toDto(levelRepository.save(level));
    }

    public void delete(Long id) {
        levelRepository.deleteById(id);
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
