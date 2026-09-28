package org.afritechinnovations.controler.academic;

import lombok.RequiredArgsConstructor;
import org.afritechinnovations.dto.academic.AcademicYearDto;
import org.afritechinnovations.service.academic.AcademicYearService;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/academic-years")
@RequiredArgsConstructor
public class AcademicYearController {

    private final AcademicYearService academicYearService;

    @GetMapping
    public List<AcademicYearDto> getBySchool(@RequestParam Long schoolId) {
        return academicYearService.findBySchool(schoolId);
    }

    @GetMapping("/current")
    public AcademicYearDto getCurrent(@RequestParam Long schoolId) {
        return academicYearService.findCurrentBySchool(schoolId);
    }

    @GetMapping("/{id}")
    public AcademicYearDto getById(@PathVariable Long id) {
        return academicYearService.findById(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public AcademicYearDto create(@RequestBody AcademicYearDto dto) {
        return academicYearService.create(dto);
    }

    @PutMapping("/{id}")
    public AcademicYearDto update(@PathVariable Long id, @RequestBody AcademicYearDto dto) {
        return academicYearService.update(id, dto);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id) {
        academicYearService.delete(id);
    }
}
