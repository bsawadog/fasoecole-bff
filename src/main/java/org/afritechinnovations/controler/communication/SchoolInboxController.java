package org.afritechinnovations.controler.communication;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.afritechinnovations.dto.communication.FamilyContactDto;
import org.afritechinnovations.service.communication.SchoolInboxService;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/** Espace propriétaire / personnel : absences signalées par les parents et messages des familles. */
@RestController
@RequestMapping("/api/owner/family")
@RequiredArgsConstructor
public class SchoolInboxController {

    private final SchoolInboxService service;

    @GetMapping("/schools/{schoolId}/summary")
    public FamilyContactDto.InboxSummary summary(@PathVariable Long schoolId) {
        return service.summary(schoolId);
    }

    @GetMapping("/schools/{schoolId}/absence-reports")
    public List<FamilyContactDto.AbsenceReportItem> absenceReports(@PathVariable Long schoolId) {
        return service.absenceReports(schoolId);
    }

    @PostMapping("/absence-reports/{reportId}/acknowledge")
    public FamilyContactDto.AbsenceReportItem acknowledge(@PathVariable Long reportId,
                                                          @Valid @RequestBody(required = false) FamilyContactDto.AbsenceDecision decision) {
        return service.acknowledge(reportId, decision);
    }

    @PostMapping("/absence-reports/{reportId}/reject")
    public FamilyContactDto.AbsenceReportItem reject(@PathVariable Long reportId,
                                                     @Valid @RequestBody FamilyContactDto.AbsenceDecision decision) {
        return service.reject(reportId, decision);
    }

    @GetMapping("/schools/{schoolId}/conversations")
    public List<FamilyContactDto.ConversationSummary> conversations(@PathVariable Long schoolId) {
        return service.conversations(schoolId);
    }

    @GetMapping("/conversations/{conversationId}")
    public FamilyContactDto.ConversationThread thread(@PathVariable Long conversationId) {
        return service.thread(conversationId);
    }

    @PostMapping("/conversations/{conversationId}/messages")
    public FamilyContactDto.ConversationThread reply(@PathVariable Long conversationId,
                                                     @Valid @RequestBody FamilyContactDto.ReplyRequest request) {
        return service.reply(conversationId, request);
    }
}