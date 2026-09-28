package org.afritechinnovations.controler.academic;

import lombok.RequiredArgsConstructor;
import org.afritechinnovations.dto.academic.AttendanceDto;
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

    @GetMapping("/by-student/{studentId}")
    public List<AttendanceDto> getByStudent(@PathVariable Long studentId) {
        return attendanceService.findByStudent(studentId);
    }

    @GetMapping("/by-class/{classId}/unjustified-absences")
    public List<AttendanceDto> getUnjustifiedAbsences(
            @PathVariable Long classId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate start,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate end) {
        return attendanceService.findUnjustifiedAbsences(classId, start, end);
    }

    @GetMapping("/{id}")
    public AttendanceDto getById(@PathVariable Long id) {
        return attendanceService.findById(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public AttendanceDto create(@RequestBody AttendanceDto dto) {
        return attendanceService.create(dto);
    }

    @PutMapping("/{id}")
    public AttendanceDto update(@PathVariable Long id, @RequestBody AttendanceDto dto) {
        return attendanceService.update(id, dto);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id) {
        attendanceService.delete(id);
    }
}
