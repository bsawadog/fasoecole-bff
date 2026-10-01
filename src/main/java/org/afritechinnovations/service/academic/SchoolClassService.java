package org.afritechinnovations.service.academic;

import lombok.RequiredArgsConstructor;
import org.afritechinnovations.dto.academic.SchoolClassDto;
import org.afritechinnovations.model.academic.AcademicYear;
import org.afritechinnovations.model.academic.Level;
import org.afritechinnovations.model.academic.SchoolClass;
import org.afritechinnovations.model.common.School;
import org.afritechinnovations.repository.academic.SchoolClassRepository;
import org.afritechinnovations.repository.academic.AcademicYearRepository;
import org.afritechinnovations.repository.academic.LevelRepository;
import org.afritechinnovations.repository.common.SchoolRepository;
import org.afritechinnovations.model.common.StaffModule;
import org.afritechinnovations.security.SchoolPermissions;
import org.springframework.stereotype.Service;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional
public class SchoolClassService {

    private final SchoolPermissions permissions;
    private final SchoolClassRepository schoolClassRepository;
    private final AcademicYearRepository academicYearRepository;
    private final LevelRepository levelRepository;
    private final SchoolRepository schoolRepository;

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

    public SchoolClassDto create(SchoolClassDto dto, Long ownerId, boolean systemAdmin) {
        if (dto.getSchoolId() == null) {
            throw new IllegalArgumentException("L'établissement est obligatoire");
        }
        School school = schoolRepository.findById(dto.getSchoolId())
                .orElseThrow(() -> new IllegalArgumentException("Établissement introuvable: " + dto.getSchoolId()));
        requireOwner(school, ownerId, systemAdmin);
        AcademicYear year = requireSchoolYear(dto.getAcademicYearId(), school.getId());
        Level level = requireSchoolLevel(dto.getLevelId(), school.getId());
        SchoolClass schoolClass = SchoolClass.builder()
                .school(school)
                .academicYear(year)
                .level(level)
                .name(dto.getName())
                .capacity(dto.getCapacity())
                .build();
        return toDto(schoolClassRepository.save(schoolClass));
    }

    public SchoolClassDto update(Long id, SchoolClassDto dto, Long ownerId, boolean systemAdmin) {
        SchoolClass schoolClass = schoolClassRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Classe introuvable: " + id));
        requireOwner(schoolClass.getSchool(), ownerId, systemAdmin);
        Long schoolId = schoolClass.getSchool().getId();
        if (!schoolId.equals(dto.getSchoolId())) {
            throw new IllegalArgumentException("Cette classe n'appartient pas à cet établissement");
        }
        AcademicYear year = requireSchoolYear(dto.getAcademicYearId(), schoolId);
        Level level = requireSchoolLevel(dto.getLevelId(), schoolId);
        schoolClass.setName(dto.getName());
        schoolClass.setCapacity(dto.getCapacity());
        schoolClass.setAcademicYear(year);
        schoolClass.setLevel(level);
        return toDto(schoolClassRepository.save(schoolClass));
    }

    private AcademicYear requireSchoolYear(Long yearId, Long schoolId) {
        if (yearId == null) {
            throw new IllegalArgumentException("L'année scolaire est obligatoire");
        }
        AcademicYear year = academicYearRepository.findById(yearId)
                .orElseThrow(() -> new IllegalArgumentException("Année scolaire introuvable: " + yearId));
        if (!schoolId.equals(year.getSchool().getId())) {
            throw new IllegalArgumentException("L'année scolaire n'appartient pas à cet établissement");
        }
        return year;
    }

    private Level requireSchoolLevel(Long levelId, Long schoolId) {
        if (levelId == null) {
            throw new IllegalArgumentException("Le niveau est obligatoire");
        }
        Level level = levelRepository.findById(levelId)
                .orElseThrow(() -> new IllegalArgumentException("Niveau introuvable: " + levelId));
        if (!schoolId.equals(level.getSchool().getId())) {
            throw new IllegalArgumentException("Le niveau n'appartient pas à cet établissement");
        }
        return level;
    }

    private void requireOwner(School school, Long ownerId, boolean systemAdmin) {
        if (!systemAdmin && !school.getOwner().getId().equals(ownerId)
                && !permissions.staffAllows(school.getId(), ownerId, StaffModule.MANAGEMENT)) {
            throw new AccessDeniedException("Vous ne pouvez gérer que les classes de votre établissement");
        }
    }

    public void delete(Long id, Long ownerId, boolean systemAdmin) {
        SchoolClass schoolClass = schoolClassRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Classe introuvable: " + id));
        requireOwner(schoolClass.getSchool(), ownerId, systemAdmin);
        schoolClassRepository.delete(schoolClass);
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
