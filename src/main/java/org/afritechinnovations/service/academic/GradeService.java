package org.afritechinnovations.service.academic;

import lombok.RequiredArgsConstructor;
import org.afritechinnovations.dto.academic.GradeDto;
import org.afritechinnovations.model.academic.ClassSubjectTeacher;
import org.afritechinnovations.model.academic.Grade;
import org.afritechinnovations.model.people.Student;
import org.afritechinnovations.repository.academic.GradeRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional
public class GradeService {

    private final GradeRepository gradeRepository;

    public List<GradeDto> findByStudent(Long studentId) {
        return gradeRepository.findByStudentIdOrderByGradeDateAsc(studentId)
                .stream()
                .map(this::toDto)
                .toList();
    }

    public List<GradeDto> findByStudentAndTerm(Long studentId, String term) {
        return gradeRepository.findByStudentIdAndTerm(studentId, term)
                .stream()
                .map(this::toDto)
                .toList();
    }

    public GradeDto findById(Long id) {
        Grade grade = gradeRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Note introuvable: " + id));
        return toDto(grade);
    }

    public GradeDto create(GradeDto dto) {
        Grade grade = Grade.builder()
                .student(Student.builder().id(dto.getStudentId()).build())
                .classSubjectTeacher(ClassSubjectTeacher.builder().id(dto.getClassSubjectTeacherId()).build())
                .term(dto.getTerm())
                .type(dto.getType())
                .value(dto.getValue())
                .maxValue(dto.getMaxValue() != null ? dto.getMaxValue() : BigDecimal.valueOf(20))
                .gradeDate(dto.getGradeDate())
                .build();
        return toDto(gradeRepository.save(grade));
    }

    public GradeDto update(Long id, GradeDto dto) {
        Grade grade = gradeRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Note introuvable: " + id));
        grade.setValue(dto.getValue());
        grade.setMaxValue(dto.getMaxValue());
        grade.setType(dto.getType());
        grade.setTerm(dto.getTerm());
        return toDto(gradeRepository.save(grade));
    }

    public void delete(Long id) {
        gradeRepository.deleteById(id);
    }

    private GradeDto toDto(Grade grade) {
        return GradeDto.builder()
                .id(grade.getId())
                .studentId(grade.getStudent().getId())
                .classSubjectTeacherId(grade.getClassSubjectTeacher().getId())
                .term(grade.getTerm())
                .type(grade.getType())
                .value(grade.getValue())
                .maxValue(grade.getMaxValue())
                .gradeDate(grade.getGradeDate())
                .build();
    }
}
