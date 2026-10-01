package org.afritechinnovations.service.academic;

import lombok.RequiredArgsConstructor;
import org.afritechinnovations.dto.academic.ClassSubjectTeacherDto;
import org.afritechinnovations.model.academic.ClassSubjectTeacher;
import org.afritechinnovations.model.academic.SchoolClass;
import org.afritechinnovations.model.academic.Subject;
import org.afritechinnovations.model.people.Teacher;
import org.afritechinnovations.repository.academic.ClassSubjectTeacherRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional
public class ClassSubjectTeacherService {

    private final ClassSubjectTeacherRepository classSubjectTeacherRepository;

    public List<ClassSubjectTeacherDto> findByClass(Long classId) {
        return classSubjectTeacherRepository.findBySchoolClassId(classId)
                .stream()
                .map(this::toDto)
                .toList();
    }

    public List<ClassSubjectTeacherDto> findByTeacher(Long teacherId) {
        return classSubjectTeacherRepository.findAllWithSubjectAndClassByTeacherId(teacherId)
                .stream()
                .map(this::toDto)
                .toList();
    }

    public ClassSubjectTeacherDto findById(Long id) {
        ClassSubjectTeacher cst = classSubjectTeacherRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Affectation introuvable: " + id));
        return toDto(cst);
    }

    public ClassSubjectTeacherDto create(ClassSubjectTeacherDto dto) {
        ClassSubjectTeacher cst = ClassSubjectTeacher.builder()
                .schoolClass(SchoolClass.builder().id(dto.getClassId()).build())
                .subject(Subject.builder().id(dto.getSubjectId()).build())
                .teacher(Teacher.builder().id(dto.getTeacherId()).build())
                .coefficient(dto.getCoefficient())
                .build();
        return toDto(classSubjectTeacherRepository.save(cst));
    }

    public ClassSubjectTeacherDto update(Long id, ClassSubjectTeacherDto dto) {
        ClassSubjectTeacher cst = classSubjectTeacherRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Affectation introuvable: " + id));
        cst.setCoefficient(dto.getCoefficient());
        return toDto(classSubjectTeacherRepository.save(cst));
    }

    public void delete(Long id) {
        classSubjectTeacherRepository.deleteById(id);
    }

    private ClassSubjectTeacherDto toDto(ClassSubjectTeacher cst) {
        return ClassSubjectTeacherDto.builder()
                .id(cst.getId())
                .classId(cst.getSchoolClass().getId())
                .subjectId(cst.getSubject().getId())
                .teacherId(cst.getTeacher().getId())
                .coefficient(cst.getCoefficient())
                .build();
    }
}
