package org.afritechinnovations.controler.people;

import lombok.RequiredArgsConstructor;
import org.afritechinnovations.dto.people.StudentDto;
import org.afritechinnovations.service.people.StudentService;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/students")
@RequiredArgsConstructor
public class StudentController {

    private final StudentService studentService;

    @GetMapping
    public List<StudentDto> getBySchool(@RequestParam Long schoolId) {
        return studentService.findBySchool(schoolId);
    }

    @GetMapping("/{id}")
    public StudentDto getById(@PathVariable Long id) {
        return studentService.findById(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public StudentDto create(@RequestBody StudentDto dto) {
        return studentService.create(dto);
    }

    @PutMapping("/{id}")
    public StudentDto update(@PathVariable Long id, @RequestBody StudentDto dto) {
        return studentService.update(id, dto);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id) {
        studentService.delete(id);
    }
}