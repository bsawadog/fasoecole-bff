package org.afritechinnovations.service.academic;

import lombok.RequiredArgsConstructor;
import org.afritechinnovations.dto.academic.AcademicYearDto;
import org.afritechinnovations.model.academic.AcademicYear;
import org.afritechinnovations.model.common.School;
import org.afritechinnovations.repository.academic.AcademicYearRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional
public class AcademicYearService {

    private final AcademicYearRepository academicYearRepository;

    public List<AcademicYearDto> findBySchool(Long schoolId) {
        return academicYearRepository.findBySchoolId(schoolId)
                .stream()
                .map(this::toDto)
                .toList();
    }

    public AcademicYearDto findCurrentBySchool(Long schoolId) {
        AcademicYear year = academicYearRepository.findBySchoolIdAndIsCurrentTrue(schoolId)
                .orElseThrow(() -> new IllegalArgumentException("Aucune année en cours pour l'école: " + schoolId));
        return toDto(year);
    }

    public AcademicYearDto findById(Long id) {
        AcademicYear year = academicYearRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Année académique introuvable: " + id));
        return toDto(year);
    }

    public AcademicYearDto create(AcademicYearDto dto) {
        AcademicYear year = AcademicYear.builder()
                .school(School.builder().id(dto.getSchoolId()).build())
                .label(dto.getLabel())
                .startDate(dto.getStartDate())
                .endDate(dto.getEndDate())
                .isCurrent(dto.getIsCurrent() != null ? dto.getIsCurrent() : false)
                .build();
        return toDto(academicYearRepository.save(year));
    }

    public AcademicYearDto update(Long id, AcademicYearDto dto) {
        AcademicYear year = academicYearRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Année académique introuvable: " + id));
        year.setLabel(dto.getLabel());
        year.setStartDate(dto.getStartDate());
        year.setEndDate(dto.getEndDate());
        year.setIsCurrent(dto.getIsCurrent());
        return toDto(academicYearRepository.save(year));
    }

    public void delete(Long id) {
        academicYearRepository.deleteById(id);
    }

    private AcademicYearDto toDto(AcademicYear year) {
        return AcademicYearDto.builder()
                .id(year.getId())
                .schoolId(year.getSchool().getId())
                .label(year.getLabel())
                .startDate(year.getStartDate())
                .endDate(year.getEndDate())
                .isCurrent(year.getIsCurrent())
                .build();
    }
}
