package org.afritechinnovations.controler.people;

import lombok.RequiredArgsConstructor;
import org.afritechinnovations.dto.people.TeacherDto;
import org.afritechinnovations.security.AccessGuard;
import org.afritechinnovations.service.people.TeacherService;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/teachers")
@RequiredArgsConstructor
public class TeacherController {

    private final TeacherService teacherService;
    private final AccessGuard guard;

    @GetMapping
    public List<TeacherDto> getBySchool(@RequestParam Long schoolId) {
        guard.requireSchoolMember(schoolId);
        return teacherService.findBySchool(schoolId);
    }

    @GetMapping("/{id}")
    public TeacherDto getById(@PathVariable Long id) {
        guard.requireSchoolMember(guard.schoolOfTeacher(id));
        return teacherService.findById(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public TeacherDto create(@RequestBody TeacherDto dto) {
        guard.requireOwnedSchool(dto.getSchoolId());
        return teacherService.create(dto);
    }

    @PutMapping("/{id}")
    public TeacherDto update(@PathVariable Long id, @RequestBody TeacherDto dto) {
        guard.requireOwnedSchool(guard.schoolOfTeacher(id));
        return teacherService.update(id, dto);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id) {
        guard.requireOwnedSchool(guard.schoolOfTeacher(id));
        teacherService.delete(id);
    }
}
