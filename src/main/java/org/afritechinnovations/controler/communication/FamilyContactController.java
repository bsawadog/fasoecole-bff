package org.afritechinnovations.controler.communication;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.afritechinnovations.dto.communication.FamilyContactDto;
import org.afritechinnovations.dto.self.SelfServiceDto;
import org.afritechinnovations.security.AccessGuard;
import org.afritechinnovations.service.communication.ParentContactService;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/** Espace parent : absences signalées et messagerie avec l'établissement. */
@RestController
@RequestMapping("/api/me")
@RequiredArgsConstructor
public class FamilyContactController {

    private final ParentContactService service;
    private final AccessGuard guard;

    @GetMapping("/students/{studentId}/absence-reports")
    public List<FamilyContactDto.AbsenceReportItem> absenceReports(@PathVariable Long studentId) {
        return service.absenceReports(guard.currentUserId(), studentId);
    }

    @PostMapping("/students/{studentId}/absence-reports")
    @ResponseStatus(HttpStatus.CREATED)
    public FamilyContactDto.AbsenceReportItem reportAbsence(@PathVariable Long studentId,
                                                            @Valid @RequestBody FamilyContactDto.AbsenceReportRequest request) {
        return service.reportAbsence(guard.currentUserId(), studentId, request);
    }

    @PostMapping("/absence-reports/{reportId}/cancel")
    public FamilyContactDto.AbsenceReportItem cancel(@PathVariable Long reportId) {
        return service.cancelReport(guard.currentUserId(), reportId);
    }

    @GetMapping("/schools")
    public List<SelfServiceDto.SchoolContact> schools() {
        return service.schools(guard.currentUserId());
    }

    @GetMapping("/conversations")
    public List<FamilyContactDto.ConversationSummary> conversations() {
        return service.conversations(guard.currentUserId());
    }

    @PostMapping("/conversations")
    @ResponseStatus(HttpStatus.CREATED)
    public FamilyContactDto.ConversationThread start(@Valid @RequestBody FamilyContactDto.NewConversationRequest request) {
        return service.start(guard.currentUserId(), request);
    }

    @GetMapping("/conversations/{conversationId}")
    public FamilyContactDto.ConversationThread thread(@PathVariable Long conversationId) {
        return service.thread(guard.currentUserId(), conversationId);
    }

    @PostMapping("/conversations/{conversationId}/messages")
    public FamilyContactDto.ConversationThread reply(@PathVariable Long conversationId,
                                                     @Valid @RequestBody FamilyContactDto.ReplyRequest request) {
        return service.reply(guard.currentUserId(), conversationId, request);
    }
}