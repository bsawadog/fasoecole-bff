package org.afritechinnovations.controler.academic;

import lombok.RequiredArgsConstructor;
import org.afritechinnovations.dto.academic.ReportCardDto;
import org.afritechinnovations.service.academic.ReportCardService;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/report-cards")
@RequiredArgsConstructor
public class ReportCardController {

    private final ReportCardService reportCardService;

    @GetMapping("/by-student/{studentId}")
    public List<ReportCardDto> getByStudent(@PathVariable Long studentId) {
        return reportCardService.findByStudent(studentId);
    }

    @GetMapping("/by-student/{studentId}/year/{academicYearId}/term/{term}")
    public ReportCardDto getByStudentYearAndTerm(
            @PathVariable Long studentId,
            @PathVariable Long academicYearId,
            @PathVariable String term) {
        return reportCardService.findByStudentAcademicYearAndTerm(studentId, academicYearId, term);
    }

    @GetMapping("/{id}")
    public ReportCardDto getById(@PathVariable Long id) {
        return reportCardService.findById(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ReportCardDto create(@RequestBody ReportCardDto dto) {
        return reportCardService.create(dto);
    }

    @PutMapping("/{id}")
    public ReportCardDto update(@PathVariable Long id, @RequestBody ReportCardDto dto) {
        return reportCardService.update(id, dto);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id) {
        reportCardService.delete(id);
    }
}
