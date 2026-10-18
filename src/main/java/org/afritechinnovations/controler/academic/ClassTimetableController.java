package org.afritechinnovations.controler.academic;
import lombok.RequiredArgsConstructor;
import org.afritechinnovations.security.AccessGuard;
import org.afritechinnovations.model.common.StaffModule;
import org.afritechinnovations.service.academic.ClassTimetableService;
import org.springframework.web.bind.annotation.*;
import org.springframework.format.annotation.DateTimeFormat;
import java.time.LocalDate;
@RestController @RequiredArgsConstructor
@RequestMapping("/api/owner/class-timetables/schools/{schoolId}")
public class ClassTimetableController {
    private final AccessGuard guard;
    private final ClassTimetableService service;
    @GetMapping public ClassTimetableService.Overview overview(@PathVariable Long schoolId,@RequestParam(required=false) Long classId,
        @RequestParam(required=false) @DateTimeFormat(iso=DateTimeFormat.ISO.DATE) LocalDate date) {
        guard.requireSchoolModule(schoolId,StaffModule.CALENDAR);
        return service.overview(schoolId,classId,date);
    }
}
