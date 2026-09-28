package org.afritechinnovations.controler.academic;

import lombok.RequiredArgsConstructor;
import org.afritechinnovations.dto.academic.SubjectDto;
import org.afritechinnovations.service.academic.SubjectService;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/subjects")
@RequiredArgsConstructor
public class SubjectController {

    private final SubjectService subjectService;

    @GetMapping
    public List<SubjectDto> getBySchool(@RequestParam Long schoolId) {
        return subjectService.findBySchool(schoolId);
    }

    @GetMapping("/{id}")
    public SubjectDto getById(@PathVariable Long id) {
        return subjectService.findById(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public SubjectDto create(@RequestBody SubjectDto dto) {
        return subjectService.create(dto);
    }

    @PutMapping("/{id}")
    public SubjectDto update(@PathVariable Long id, @RequestBody SubjectDto dto) {
        return subjectService.update(id, dto);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id) {
        subjectService.delete(id);
    }
}
