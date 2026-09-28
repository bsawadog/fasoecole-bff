package org.afritechinnovations.service.people;

import lombok.RequiredArgsConstructor;
import org.afritechinnovations.dto.people.TeacherDto;
import org.afritechinnovations.model.common.School;
import org.afritechinnovations.model.common.User;
import org.afritechinnovations.model.people.Teacher;
import org.afritechinnovations.repository.people.TeacherRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional
public class TeacherService {

    private final TeacherRepository teacherRepository;

    public List<TeacherDto> findBySchool(Long schoolId) {
        return teacherRepository.findBySchoolId(schoolId)
                .stream()
                .map(this::toDto)
                .toList();
    }

    public TeacherDto findById(Long id) {
        Teacher teacher = teacherRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Enseignant introuvable: " + id));
        return toDto(teacher);
    }

    public TeacherDto create(TeacherDto dto) {
        Teacher teacher = Teacher.builder()
                .user(User.builder().id(dto.getUserId()).build())
                .school(School.builder().id(dto.getSchoolId()).build())
                .specialty(dto.getSpecialty())
                .hireDate(dto.getHireDate())
                .build();
        return toDto(teacherRepository.save(teacher));
    }

    public TeacherDto update(Long id, TeacherDto dto) {
        Teacher teacher = teacherRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Enseignant introuvable: " + id));
        teacher.setSpecialty(dto.getSpecialty());
        teacher.setHireDate(dto.getHireDate());
        return toDto(teacherRepository.save(teacher));
    }

    public void delete(Long id) {
        teacherRepository.deleteById(id);
    }

    private TeacherDto toDto(Teacher teacher) {
        return TeacherDto.builder()
                .id(teacher.getId())
                .userId(teacher.getUser().getId())
                .schoolId(teacher.getSchool().getId())
                .specialty(teacher.getSpecialty())
                .hireDate(teacher.getHireDate())
                .build();
    }
}