package org.afritechinnovations.controler.academic;

import lombok.RequiredArgsConstructor;
import org.afritechinnovations.dto.academic.SchoolClassDto;
import org.afritechinnovations.service.academic.SchoolClassService;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/classes")
@RequiredArgsConstructor
public class SchoolClassController {

    private final SchoolClassService schoolClassService;

    @GetMapping
    public List<SchoolClassDto> getBySchool(@RequestParam Long schoolId) {
        return schoolClassService.findBySchool(schoolId);
    }

    @GetMapping("/by-year/{academicYearId}")
    public List<SchoolClassDto> getByAcademicYear(@PathVariable Long academicYearId) {
        return schoolClassService.findByAcademicYear(academicYearId);
    }

    @GetMapping("/with-level")
    public List<SchoolClassDto> getWithLevel(@RequestParam Long schoolId, @RequestParam Long academicYearId) {
        return schoolClassService.findWithLevelBySchoolAndYear(schoolId, academicYearId);
    }

    @GetMapping("/{id}")
    public SchoolClassDto getById(@PathVariable Long id) {
        return schoolClassService.findById(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public SchoolClassDto create(@RequestBody SchoolClassDto dto) {
        return schoolClassService.create(dto);
    }

    @PutMapping("/{id}")
    public SchoolClassDto update(@PathVariable Long id, @RequestBody SchoolClassDto dto) {
        return schoolClassService.update(id, dto);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id) {
        schoolClassService.delete(id);
    }
}
