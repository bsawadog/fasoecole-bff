package org.afritechinnovations.controler.academic;

import lombok.RequiredArgsConstructor;
import org.afritechinnovations.dto.academic.AttendanceDto;
import org.afritechinnovations.security.AccessGuard;
import org.afritechinnovations.service.academic.AttendanceService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/attendances")
@RequiredArgsConstructor
public class AttendanceController {

    private final AttendanceService attendanceService;
    private final AccessGuard guard;

    @GetMapping("/by-student/{studentId}")
    public List<AttendanceDto> getByStudent(@PathVariable Long studentId) {
        guard.requireStudentReader(studentId);
        return attendanceService.findByStudent(studentId);
    }

    @GetMapping("/by-class/{classId}/unjustified-absences")
    public List<AttendanceDto> getUnjustifiedAbsences(
            @PathVariable Long classId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate start,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate end) {
        guard.requireSchoolStaff(guard.schoolOfClass(classId));
        return attendanceService.findUnjustifiedAbsences(classId, start, end);
    }

    @GetMapping("/{id}")
    public AttendanceDto getById(@PathVariable Long id) {
        AttendanceDto attendance = attendanceService.findById(id);
        guard.requireStudentReader(attendance.getStudentId());
        return attendance;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public AttendanceDto create(@RequestBody AttendanceDto dto) {
        requireStaffFor(dto.getStudentId(), dto.getClassId());
        return attendanceService.create(dto);
    }

    @PutMapping("/{id}")
    public AttendanceDto update(@PathVariable Long id, @RequestBody AttendanceDto dto) {
        AttendanceDto existing = attendanceService.findById(id);
        requireStaffFor(existing.getStudentId(), existing.getClassId());
        return attendanceService.update(id, dto);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id) {
        AttendanceDto existing = attendanceService.findById(id);
        requireStaffFor(existing.getStudentId(), existing.getClassId());
        attendanceService.delete(id);
    }

    private void requireStaffFor(Long studentId, Long classId) {
        Long schoolId = guard.schoolOfClass(classId);
        guard.requireSame(schoolId, guard.schoolOfStudent(studentId), "élève");
        guard.requireSchoolStaff(schoolId);
    }
}
