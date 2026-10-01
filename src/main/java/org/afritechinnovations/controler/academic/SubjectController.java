package org.afritechinnovations.controler.academic;

import lombok.RequiredArgsConstructor;
import org.afritechinnovations.dto.academic.SubjectDto;
import org.afritechinnovations.model.common.StaffModule;
import org.afritechinnovations.security.AccessGuard;
import org.afritechinnovations.service.academic.SubjectService;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/subjects")
@RequiredArgsConstructor
public class SubjectController {

    private final SubjectService subjectService;
    private final AccessGuard guard;

    @GetMapping
    public List<SubjectDto> getBySchool(@RequestParam Long schoolId) {
        guard.requireSchoolMember(schoolId);
        return subjectService.findBySchool(schoolId);
    }

    @GetMapping("/{id}")
    public SubjectDto getById(@PathVariable Long id) {
        SubjectDto subject = subjectService.findById(id);
        guard.requireSchoolMember(subject.getSchoolId());
        return subject;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public SubjectDto create(@RequestBody SubjectDto dto) {
        guard.requireSchoolModule(dto.getSchoolId(), StaffModule.MANAGEMENT);
        return subjectService.create(dto);
    }

    @PutMapping("/{id}")
    public SubjectDto update(@PathVariable Long id, @RequestBody SubjectDto dto) {
        guard.requireSchoolModule(subjectService.findById(id).getSchoolId(), StaffModule.MANAGEMENT);
        return subjectService.update(id, dto);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id) {
        guard.requireSchoolModule(subjectService.findById(id).getSchoolId(), StaffModule.MANAGEMENT);
        subjectService.delete(id);
    }
}
