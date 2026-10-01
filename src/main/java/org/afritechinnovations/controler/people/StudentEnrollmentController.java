package org.afritechinnovations.controler.people;

import lombok.RequiredArgsConstructor;
import org.afritechinnovations.dto.people.StudentEnrollmentDto;
import org.afritechinnovations.model.people.EnrollmentStatus;
import org.afritechinnovations.security.AccessGuard;
import org.afritechinnovations.service.people.StudentEnrollmentService;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/enrollments")
@RequiredArgsConstructor
public class StudentEnrollmentController {

    private final StudentEnrollmentService studentEnrollmentService;
    private final AccessGuard guard;

    @GetMapping("/by-student/{studentId}")
    public List<StudentEnrollmentDto> getByStudent(@PathVariable Long studentId) {
        guard.requireStudentReader(studentId);
        return studentEnrollmentService.findByStudent(studentId);
    }

    @GetMapping("/by-class/{classId}/active")
    public List<StudentEnrollmentDto> getActiveByClass(@PathVariable Long classId) {
        guard.requireSchoolStaff(guard.schoolOfClass(classId));
        return studentEnrollmentService.findActiveByClass(classId);
    }

    @GetMapping("/{id}")
    public StudentEnrollmentDto getById(@PathVariable Long id) {
        StudentEnrollmentDto enrollment = studentEnrollmentService.findById(id);
        guard.requireStudentReader(enrollment.getStudentId());
        return enrollment;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public StudentEnrollmentDto create(@RequestBody StudentEnrollmentDto dto) {
        Long schoolId = guard.schoolOfClass(dto.getClassId());
        guard.requireOwnedSchool(schoolId);
        guard.requireSame(schoolId, guard.schoolOfStudent(dto.getStudentId()), "élève");
        guard.requireSame(schoolId, guard.schoolOfYear(dto.getAcademicYearId()), "année scolaire");
        return studentEnrollmentService.create(dto);
    }

    @PatchMapping("/{id}/status")
    public StudentEnrollmentDto updateStatus(@PathVariable Long id, @RequestParam EnrollmentStatus status) {
        guard.requireOwnedSchool(guard.schoolOfClass(studentEnrollmentService.findById(id).getClassId()));
        return studentEnrollmentService.updateStatus(id, status);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id) {
        guard.requireOwnedSchool(guard.schoolOfClass(studentEnrollmentService.findById(id).getClassId()));
        studentEnrollmentService.delete(id);
    }
}
