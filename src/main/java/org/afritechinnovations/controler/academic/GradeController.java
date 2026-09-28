package org.afritechinnovations.controler.academic;

import lombok.RequiredArgsConstructor;
import org.afritechinnovations.dto.academic.GradeDto;
import org.afritechinnovations.service.academic.GradeService;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/grades")
@RequiredArgsConstructor
public class GradeController {

    private final GradeService gradeService;

    @GetMapping("/by-student/{studentId}")
    public List<GradeDto> getByStudent(@PathVariable Long studentId) {
        return gradeService.findByStudent(studentId);
    }

    @GetMapping("/by-student/{studentId}/term/{term}")
    public List<GradeDto> getByStudentAndTerm(@PathVariable Long studentId, @PathVariable String term) {
        return gradeService.findByStudentAndTerm(studentId, term);
    }

    @GetMapping("/{id}")
    public GradeDto getById(@PathVariable Long id) {
        return gradeService.findById(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public GradeDto create(@RequestBody GradeDto dto) {
        return gradeService.create(dto);
    }

    @PutMapping("/{id}")
    public GradeDto update(@PathVariable Long id, @RequestBody GradeDto dto) {
        return gradeService.update(id, dto);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id) {
        gradeService.delete(id);
    }
}
