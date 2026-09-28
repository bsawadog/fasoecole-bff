package org.afritechinnovations.service.people;

import lombok.RequiredArgsConstructor;
import org.afritechinnovations.dto.people.StudentEnrollmentDto;
import org.afritechinnovations.model.academic.AcademicYear;
import org.afritechinnovations.model.academic.SchoolClass;
import org.afritechinnovations.model.people.EnrollmentStatus;
import org.afritechinnovations.model.people.Student;
import org.afritechinnovations.model.people.StudentEnrollment;
import org.afritechinnovations.repository.people.StudentEnrollmentRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional
public class StudentEnrollmentService {

    private final StudentEnrollmentRepository studentEnrollmentRepository;

    public List<StudentEnrollmentDto> findByStudent(Long studentId) {
        return studentEnrollmentRepository.findByStudentId(studentId)
                .stream()
                .map(this::toDto)
                .toList();
    }

    public List<StudentEnrollmentDto> findActiveByClass(Long classId) {
        return studentEnrollmentRepository.findBySchoolClassIdAndStatus(classId, EnrollmentStatus.ACTIVE)
                .stream()
                .map(this::toDto)
                .toList();
    }

    public StudentEnrollmentDto findById(Long id) {
        StudentEnrollment enrollment = studentEnrollmentRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Inscription introuvable: " + id));
        return toDto(enrollment);
    }

    public StudentEnrollmentDto create(StudentEnrollmentDto dto) {
        StudentEnrollment enrollment = StudentEnrollment.builder()
                .student(Student.builder().id(dto.getStudentId()).build())
                .schoolClass(SchoolClass.builder().id(dto.getClassId()).build())
                .academicYear(AcademicYear.builder().id(dto.getAcademicYearId()).build())
                .status(dto.getStatus() != null ? dto.getStatus() : EnrollmentStatus.ACTIVE)
                .enrollmentDate(dto.getEnrollmentDate())
                .build();
        return toDto(studentEnrollmentRepository.save(enrollment));
    }

    public StudentEnrollmentDto updateStatus(Long id, EnrollmentStatus status) {
        StudentEnrollment enrollment = studentEnrollmentRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Inscription introuvable: " + id));
        enrollment.setStatus(status);
        return toDto(studentEnrollmentRepository.save(enrollment));
    }

    public void delete(Long id) {
        studentEnrollmentRepository.deleteById(id);
    }

    private StudentEnrollmentDto toDto(StudentEnrollment enrollment) {
        return StudentEnrollmentDto.builder()
                .id(enrollment.getId())
                .studentId(enrollment.getStudent().getId())
                .classId(enrollment.getSchoolClass().getId())
                .academicYearId(enrollment.getAcademicYear().getId())
                .status(enrollment.getStatus())
                .enrollmentDate(enrollment.getEnrollmentDate())
                .build();
    }
}
