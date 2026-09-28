package org.afritechinnovations.service.academic;

import lombok.RequiredArgsConstructor;
import org.afritechinnovations.dto.academic.SchoolClassDto;
import org.afritechinnovations.model.academic.AcademicYear;
import org.afritechinnovations.model.academic.Level;
import org.afritechinnovations.model.academic.SchoolClass;
import org.afritechinnovations.model.common.School;
import org.afritechinnovations.repository.academic.SchoolClassRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional
public class SchoolClassService {

    private final SchoolClassRepository schoolClassRepository;

    public List<SchoolClassDto> findBySchool(Long schoolId) {
        return schoolClassRepository.findBySchoolId(schoolId)
                .stream()
                .map(this::toDto)
                .toList();
    }

    public List<SchoolClassDto> findByAcademicYear(Long academicYearId) {
        return schoolClassRepository.findByAcademicYearId(academicYearId)
                .stream()
                .map(this::toDto)
                .toList();
    }

    public List<SchoolClassDto> findWithLevelBySchoolAndYear(Long schoolId, Long academicYearId) {
        return schoolClassRepository.findAllWithLevelBySchoolAndYear(schoolId, academicYearId)
                .stream()
                .map(this::toDto)
                .toList();
    }

    public SchoolClassDto findById(Long id) {
        SchoolClass schoolClass = schoolClassRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Classe introuvable: " + id));
        return toDto(schoolClass);
    }

    public SchoolClassDto create(SchoolClassDto dto) {
        SchoolClass schoolClass = SchoolClass.builder()
                .school(School.builder().id(dto.getSchoolId()).build())
                .academicYear(AcademicYear.builder().id(dto.getAcademicYearId()).build())
                .level(Level.builder().id(dto.getLevelId()).build())
                .name(dto.getName())
                .capacity(dto.getCapacity())
                .build();
        return toDto(schoolClassRepository.save(schoolClass));
    }

    public SchoolClassDto update(Long id, SchoolClassDto dto) {
        SchoolClass schoolClass = schoolClassRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Classe introuvable: " + id));
        schoolClass.setName(dto.getName());
        schoolClass.setCapacity(dto.getCapacity());
        schoolClass.setLevel(Level.builder().id(dto.getLevelId()).build());
        return toDto(schoolClassRepository.save(schoolClass));
    }

    public void delete(Long id) {
        schoolClassRepository.deleteById(id);
    }

    private SchoolClassDto toDto(SchoolClass schoolClass) {
        return SchoolClassDto.builder()
                .id(schoolClass.getId())
                .schoolId(schoolClass.getSchool().getId())
                .academicYearId(schoolClass.getAcademicYear().getId())
                .levelId(schoolClass.getLevel().getId())
                .name(schoolClass.getName())
                .capacity(schoolClass.getCapacity())
                .build();
    }
}
