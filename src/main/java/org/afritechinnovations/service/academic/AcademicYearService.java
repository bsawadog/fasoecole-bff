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
        List<AcademicYear> existing = academicYearRepository.lockSchoolYears(dto.getSchoolId());
        validate(dto,existing,null);
        if (Boolean.TRUE.equals(dto.getIsCurrent()) && existing.stream().anyMatch(y -> Boolean.TRUE.equals(y.getIsCurrent())))
            throw new IllegalArgumentException("Utilisez Clôturer année pour changer l'année en cours.");
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
        year.requireOpen();
        List<AcademicYear> existing = academicYearRepository.lockSchoolYears(year.getSchool().getId());
        validate(dto,existing,id);
        if (dto.getIsCurrent()!=null && !dto.getIsCurrent().equals(year.getIsCurrent()))
            throw new IllegalArgumentException("Utilisez Clôturer année pour changer l'année en cours.");
        year.setLabel(dto.getLabel());
        year.setStartDate(dto.getStartDate());
        year.setEndDate(dto.getEndDate());
        return toDto(academicYearRepository.save(year));
    }

    public void delete(Long id) {
        academicYearRepository.findById(id).orElseThrow().requireOpen();
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
                .closed(year.isClosed())
                .build();
    }
    private void validate(AcademicYearDto dto,List<AcademicYear> existing,Long id) {
        if(dto.getLabel()==null || dto.getLabel().isBlank() || dto.getLabel().trim().length()>20
                || dto.getStartDate()==null || dto.getEndDate()==null || !dto.getEndDate().isAfter(dto.getStartDate()))
            throw new IllegalArgumentException("Libellé et dates de l'année scolaire invalides.");
        for(var other : existing) {
            if(java.util.Objects.equals(other.getId(),id)) continue;
            if(other.getLabel().equalsIgnoreCase(dto.getLabel().trim())) throw new IllegalArgumentException("Ce libellé d'année existe déjà.");
            if(!dto.getStartDate().isAfter(other.getEndDate()) && !dto.getEndDate().isBefore(other.getStartDate()))
                throw new IllegalArgumentException("Les dates chevauchent une autre année scolaire.");
        }
    }
}
