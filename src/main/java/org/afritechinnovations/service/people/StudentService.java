package org.afritechinnovations.service.people;

import lombok.RequiredArgsConstructor;
import org.afritechinnovations.dto.people.StudentDto;
import org.afritechinnovations.model.common.School;
import org.afritechinnovations.model.people.Student;
import org.afritechinnovations.repository.people.StudentRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional
public class StudentService {

    private final StudentRepository studentRepository;
    private final org.afritechinnovations.repository.common.UserRepository userRepository;

    public List<StudentDto> findBySchool(Long schoolId) {
        return studentRepository.findBySchoolId(schoolId)
                .stream()
                .map(this::toDto)
                .toList();
    }

    public StudentDto findById(Long id) {
        Student student = studentRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Élève introuvable: " + id));
        return toDto(student);
    }

    public StudentDto create(StudentDto dto) {
        Student student = Student.builder()
                .user(userRepository.getReferenceById(dto.getUserId()))
                .school(School.builder().id(dto.getSchoolId()).build())
                .registrationNumber(dto.getRegistrationNumber())
                .birthDate(dto.getBirthDate())
                .gender(dto.getGender())
                .build();
        return toDto(studentRepository.save(student));
    }

    public StudentDto update(Long id, StudentDto dto) {
        Student student = studentRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Élève introuvable: " + id));
        student.setRegistrationNumber(dto.getRegistrationNumber());
        student.setBirthDate(dto.getBirthDate());
        student.setGender(dto.getGender());
        return toDto(studentRepository.save(student));
    }

    public void delete(Long id) {
        studentRepository.deleteById(id);
    }

    private StudentDto toDto(Student student) {
        return StudentDto.builder()
                .id(student.getId())
                .userId(student.getUser().getId())
                .schoolId(student.getSchool().getId())
                .registrationNumber(student.getRegistrationNumber())
                .birthDate(student.getBirthDate())
                .gender(student.getGender())
                .build();
    }
}