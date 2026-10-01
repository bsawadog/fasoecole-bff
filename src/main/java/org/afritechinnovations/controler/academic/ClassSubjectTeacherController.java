package org.afritechinnovations.controler.academic;

import lombok.RequiredArgsConstructor;
import org.afritechinnovations.dto.academic.ClassSubjectTeacherDto;
import org.afritechinnovations.model.common.StaffModule;
import org.afritechinnovations.security.AccessGuard;
import org.afritechinnovations.service.academic.ClassSubjectTeacherService;
import org.afritechinnovations.service.academic.SubjectService;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/class-subject-teachers")
@RequiredArgsConstructor
public class ClassSubjectTeacherController {

    private final ClassSubjectTeacherService classSubjectTeacherService;
    private final SubjectService subjectService;
    private final AccessGuard guard;

    @GetMapping("/by-class/{classId}")
    public List<ClassSubjectTeacherDto> getByClass(@PathVariable Long classId) {
        guard.requireSchoolMember(guard.schoolOfClass(classId));
        return classSubjectTeacherService.findByClass(classId);
    }

    @GetMapping("/by-teacher/{teacherId}")
    public List<ClassSubjectTeacherDto> getByTeacher(@PathVariable Long teacherId) {
        guard.requireSchoolStaff(guard.schoolOfTeacher(teacherId));
        return classSubjectTeacherService.findByTeacher(teacherId);
    }

    @GetMapping("/{id}")
    public ClassSubjectTeacherDto getById(@PathVariable Long id) {
        ClassSubjectTeacherDto assignment = classSubjectTeacherService.findById(id);
        guard.requireSchoolMember(guard.schoolOfClass(assignment.getClassId()));
        return assignment;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ClassSubjectTeacherDto create(@RequestBody ClassSubjectTeacherDto dto) {
        Long schoolId = guard.schoolOfClass(dto.getClassId());
        guard.requireSchoolModule(schoolId, StaffModule.MANAGEMENT, StaffModule.TEACHERS);
        guard.requireSame(schoolId, guard.schoolOfTeacher(dto.getTeacherId()), "enseignant");
        guard.requireSame(schoolId, subjectService.findById(dto.getSubjectId()).getSchoolId(), "matière");
        return classSubjectTeacherService.create(dto);
    }

    @PutMapping("/{id}")
    public ClassSubjectTeacherDto update(@PathVariable Long id, @RequestBody ClassSubjectTeacherDto dto) {
        guard.requireSchoolModule(guard.schoolOfClass(classSubjectTeacherService.findById(id).getClassId()), StaffModule.MANAGEMENT, StaffModule.TEACHERS);
        return classSubjectTeacherService.update(id, dto);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id) {
        guard.requireSchoolModule(guard.schoolOfClass(classSubjectTeacherService.findById(id).getClassId()), StaffModule.MANAGEMENT, StaffModule.TEACHERS);
        classSubjectTeacherService.delete(id);
    }
}
