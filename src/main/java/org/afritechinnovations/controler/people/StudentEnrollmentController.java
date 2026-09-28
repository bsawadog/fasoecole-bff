package org.afritechinnovations.controler.people;

import lombok.RequiredArgsConstructor;
import org.afritechinnovations.dto.people.StudentEnrollmentDto;
import org.afritechinnovations.model.people.EnrollmentStatus;
import org.afritechinnovations.service.people.StudentEnrollmentService;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/enrollments")
@RequiredArgsConstructor
public class StudentEnrollmentController {

    private final StudentEnrollmentService studentEnrollmentService;

    @GetMapping("/by-student/{studentId}")
    public List<StudentEnrollmentDto> getByStudent(@PathVariable Long studentId) {
        return studentEnrollmentService.findByStudent(studentId);
    }

    @GetMapping("/by-class/{classId}/active")
    public List<StudentEnrollmentDto> getActiveByClass(@PathVariable Long classId) {
        return studentEnrollmentService.findActiveByClass(classId);
    }

    @GetMapping("/{id}")
    public StudentEnrollmentDto getById(@PathVariable Long id) {
        return studentEnrollmentService.findById(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public StudentEnrollmentDto create(@RequestBody StudentEnrollmentDto dto) {
        return studentEnrollmentService.create(dto);
    }

    @PatchMapping("/{id}/status")
    public StudentEnrollmentDto updateStatus(@PathVariable Long id, @RequestParam EnrollmentStatus status) {
        return studentEnrollmentService.updateStatus(id, status);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id) {
        studentEnrollmentService.delete(id);
    }
}
