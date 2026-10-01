package org.afritechinnovations.service.academic;

import lombok.RequiredArgsConstructor;
import org.afritechinnovations.dto.academic.SubjectDto;
import org.afritechinnovations.model.academic.Subject;
import org.afritechinnovations.model.common.School;
import org.afritechinnovations.repository.academic.SubjectRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional
public class SubjectService {

    private static final BigDecimal MIN_COEFFICIENT = new BigDecimal("0.25");
    private static final BigDecimal MAX_COEFFICIENT = new BigDecimal("20");

    private final SubjectRepository subjectRepository;

    public List<SubjectDto> findBySchool(Long schoolId) {
        return subjectRepository.findBySchoolId(schoolId)
                .stream()
                .map(this::toDto)
                .toList();
    }

    public SubjectDto findById(Long id) {
        Subject subject = subjectRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Matière introuvable: " + id));
        return toDto(subject);
    }

    public SubjectDto create(SubjectDto dto) {
        Subject subject = Subject.builder()
                .school(School.builder().id(dto.getSchoolId()).build())
                .name(requireName(dto.getName()))
                .code(dto.getCode())
                .coefficient(validCoefficient(dto.getCoefficient()))
                .build();
        return toDto(subjectRepository.save(subject));
    }

    public SubjectDto update(Long id, SubjectDto dto) {
        Subject subject = subjectRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Matière introuvable: " + id));
        subject.setName(requireName(dto.getName()));
        subject.setCode(dto.getCode());
        if (dto.getCoefficient() != null) {
            subject.setCoefficient(validCoefficient(dto.getCoefficient()));
        }
        return toDto(subjectRepository.save(subject));
    }

    public void delete(Long id) {
        subjectRepository.deleteById(id);
    }

    private static String requireName(String name) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("Le nom de la matière est obligatoire");
        }
        return name.trim();
    }

    static BigDecimal validCoefficient(BigDecimal coefficient) {
        if (coefficient == null) {
            return BigDecimal.ONE.setScale(2);
        }
        if (coefficient.compareTo(MIN_COEFFICIENT) < 0 || coefficient.compareTo(MAX_COEFFICIENT) > 0) {
            throw new IllegalArgumentException("Le coefficient doit être compris entre 0,25 et 20");
        }
        return coefficient.setScale(2, RoundingMode.HALF_UP);
    }

    private SubjectDto toDto(Subject subject) {
        return SubjectDto.builder()
                .id(subject.getId())
                .schoolId(subject.getSchool().getId())
                .name(subject.getName())
                .code(subject.getCode())
                .coefficient(subject.getCoefficient())
                .build();
    }
}