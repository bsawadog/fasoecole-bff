package org.afritechinnovations.controler.academic;

import lombok.RequiredArgsConstructor;
import org.afritechinnovations.dto.academic.AcademicYearDto;
import org.afritechinnovations.model.common.StaffModule;
import org.afritechinnovations.security.AccessGuard;
import org.afritechinnovations.service.academic.AcademicYearService;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/academic-years")
@RequiredArgsConstructor
public class AcademicYearController {

    private final AcademicYearService academicYearService;
    private final AccessGuard guard;

    @GetMapping
    public List<AcademicYearDto> getBySchool(@RequestParam Long schoolId) {
        guard.requireSchoolMember(schoolId);
        return academicYearService.findBySchool(schoolId);
    }

    @GetMapping("/current")
    public AcademicYearDto getCurrent(@RequestParam Long schoolId) {
        guard.requireSchoolMember(schoolId);
        return academicYearService.findCurrentBySchool(schoolId);
    }

    @GetMapping("/{id}")
    public AcademicYearDto getById(@PathVariable Long id) {
        AcademicYearDto year = academicYearService.findById(id);
        guard.requireSchoolMember(year.getSchoolId());
        return year;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public AcademicYearDto create(@RequestBody AcademicYearDto dto) {
        guard.requireSchoolModule(dto.getSchoolId(), StaffModule.MANAGEMENT);
        return academicYearService.create(dto);
    }

    @PutMapping("/{id}")
    public AcademicYearDto update(@PathVariable Long id, @RequestBody AcademicYearDto dto) {
        guard.requireSchoolModule(academicYearService.findById(id).getSchoolId(), StaffModule.MANAGEMENT);
        return academicYearService.update(id, dto);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id) {
        guard.requireSchoolModule(academicYearService.findById(id).getSchoolId(), StaffModule.MANAGEMENT);
        academicYearService.delete(id);
    }
}
