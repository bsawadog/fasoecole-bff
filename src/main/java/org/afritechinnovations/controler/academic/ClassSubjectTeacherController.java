package org.afritechinnovations.controler.academic;

import lombok.RequiredArgsConstructor;
import org.afritechinnovations.dto.academic.ClassSubjectTeacherDto;
import org.afritechinnovations.service.academic.ClassSubjectTeacherService;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/class-subject-teachers")
@RequiredArgsConstructor
public class ClassSubjectTeacherController {

    private final ClassSubjectTeacherService classSubjectTeacherService;

    @GetMapping("/by-class/{classId}")
    public List<ClassSubjectTeacherDto> getByClass(@PathVariable Long classId) {
        return classSubjectTeacherService.findByClass(classId);
    }

    @GetMapping("/by-teacher/{teacherId}")
    public List<ClassSubjectTeacherDto> getByTeacher(@PathVariable Long teacherId) {
        return classSubjectTeacherService.findByTeacher(teacherId);
    }

    @GetMapping("/{id}")
    public ClassSubjectTeacherDto getById(@PathVariable Long id) {
        return classSubjectTeacherService.findById(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ClassSubjectTeacherDto create(@RequestBody ClassSubjectTeacherDto dto) {
        return classSubjectTeacherService.create(dto);
    }

    @PutMapping("/{id}")
    public ClassSubjectTeacherDto update(@PathVariable Long id, @RequestBody ClassSubjectTeacherDto dto) {
        return classSubjectTeacherService.update(id, dto);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id) {
        classSubjectTeacherService.delete(id);
    }
}
