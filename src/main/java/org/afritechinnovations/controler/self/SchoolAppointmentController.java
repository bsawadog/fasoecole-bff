package org.afritechinnovations.controler.self;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.afritechinnovations.dto.self.SchoolAppointmentDto.*;
import org.afritechinnovations.dto.self.ParentPortalDto.AppointmentDecision;
import org.afritechinnovations.dto.communication.FamilyContactDto.Recipient;
import org.afritechinnovations.service.self.SchoolAppointmentService;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class SchoolAppointmentController {
    private final SchoolAppointmentService service;
    @GetMapping("/owner/appointments/schools/{schoolId}/recipients")
    public List<Recipient> recipients(@PathVariable Long schoolId) { return service.recipients(schoolId); }
    @GetMapping("/owner/appointments/schools/{schoolId}")
    public List<Appointment> list(@PathVariable Long schoolId) { return service.schoolAppointments(schoolId); }
    @PostMapping("/owner/appointments/schools/{schoolId}") @ResponseStatus(HttpStatus.CREATED)
    public Appointment create(@PathVariable Long schoolId, @Valid @RequestBody Request request) { return service.create(schoolId, request); }
    @PostMapping("/owner/appointments/schools/{schoolId}/{id}/cancel") @ResponseStatus(HttpStatus.NO_CONTENT)
    public void cancel(@PathVariable Long schoolId, @PathVariable Long id) { service.cancel(schoolId, id); }
    @GetMapping("/me/appointments")
    public List<Appointment> received() { return service.received(); }
    @PostMapping("/me/appointments/{id}/decision") @ResponseStatus(HttpStatus.NO_CONTENT)
    public void decide(@PathVariable Long id, @Valid @RequestBody AppointmentDecision request) { service.decide(id, request); }
}
