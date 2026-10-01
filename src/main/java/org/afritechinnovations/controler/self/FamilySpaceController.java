package org.afritechinnovations.controler.self;

import lombok.RequiredArgsConstructor;
import org.afritechinnovations.dto.self.SelfServiceDto;
import org.afritechinnovations.security.AccessGuard;
import org.afritechinnovations.service.self.FamilySpaceService;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/** Espaces élève et parent : dossiers de l'élève connecté ou des enfants du parent connecté. */
@RestController
@RequestMapping("/api/me/students")
@RequiredArgsConstructor
public class FamilySpaceController {

    private final FamilySpaceService service;
    private final AccessGuard guard;

    @GetMapping
    public List<SelfServiceDto.StudentOverview> students() {
        return service.students(guard.currentUserId());
    }

    @GetMapping("/{studentId}")
    public SelfServiceDto.StudentOverview student(@PathVariable Long studentId) {
        return service.student(guard.currentUserId(), studentId);
    }

    @GetMapping("/{studentId}/profile")
    public SelfServiceDto.StudentProfile profile(@PathVariable Long studentId) {
        return service.profile(guard.currentUserId(), studentId);
    }

    @GetMapping("/{studentId}/grades")
    public SelfServiceDto.StudentGrades grades(@PathVariable Long studentId) {
        return service.grades(guard.currentUserId(), studentId);
    }

    @GetMapping("/{studentId}/schedule")
    public List<SelfServiceDto.ScheduleEntry> schedule(@PathVariable Long studentId) {
        return service.schedule(guard.currentUserId(), studentId);
    }

    @GetMapping("/{studentId}/attendance")
    public List<SelfServiceDto.AttendanceItem> attendance(@PathVariable Long studentId) {
        return service.attendance(guard.currentUserId(), studentId);
    }

    @GetMapping("/{studentId}/invoices")
    public List<SelfServiceDto.InvoiceItem> invoices(@PathVariable Long studentId) {
        return service.invoices(guard.currentUserId(), studentId);
    }
}
