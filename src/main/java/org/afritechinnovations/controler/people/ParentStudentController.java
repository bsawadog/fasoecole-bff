package org.afritechinnovations.controler.people;

import lombok.RequiredArgsConstructor;
import org.afritechinnovations.dto.people.ParentStudentDto;
import org.afritechinnovations.service.people.ParentStudentService;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/parent-students")
@RequiredArgsConstructor
public class ParentStudentController {

    private final ParentStudentService parentStudentService;

    @GetMapping("/by-parent/{parentId}")
    public List<ParentStudentDto> getByParent(@PathVariable Long parentId) {
        return parentStudentService.findByParent(parentId);
    }

    @GetMapping("/by-student/{studentId}")
    public List<ParentStudentDto> getByStudent(@PathVariable Long studentId) {
        return parentStudentService.findByStudent(studentId);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ParentStudentDto create(@RequestBody ParentStudentDto dto) {
        return parentStudentService.create(dto);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id) {
        parentStudentService.delete(id);
    }
}
