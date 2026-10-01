package org.afritechinnovations.service.communication;

import lombok.RequiredArgsConstructor;
import org.afritechinnovations.dto.communication.FamilyContactDto;
import org.afritechinnovations.model.academic.Attendance;
import org.afritechinnovations.model.academic.AttendanceStatus;
import org.afritechinnovations.model.common.StaffModule;
import org.afritechinnovations.model.common.User;
import org.afritechinnovations.model.communication.AbsenceReport;
import org.afritechinnovations.model.communication.AbsenceReportStatus;
import org.afritechinnovations.model.communication.SchoolConversation;
import org.afritechinnovations.model.communication.SchoolConversationMessage;
import org.afritechinnovations.repository.academic.AttendanceRepository;
import org.afritechinnovations.repository.common.UserRepository;
import org.afritechinnovations.repository.communication.AbsenceReportRepository;
import org.afritechinnovations.repository.communication.SchoolConversationMessageRepository;
import org.afritechinnovations.repository.communication.SchoolConversationRepository;
import org.afritechinnovations.security.AccessGuard;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Côté établissement (propriétaire ou personnel disposant du module « Élèves ») : absences signalées par les
 * parents et messages reçus des familles.
 */
@Service
@RequiredArgsConstructor
@Transactional
public class SchoolInboxService {

    private final AccessGuard guard;
    private final AbsenceReportRepository reportRepository;
    private final AttendanceRepository attendanceRepository;
    private final SchoolConversationRepository conversationRepository;
    private final SchoolConversationMessageRepository messageRepository;
    private final UserRepository userRepository;

    @Transactional(readOnly = true)
    public FamilyContactDto.InboxSummary summary(Long schoolId) {
        guard.requireSchoolModule(schoolId, StaffModule.STUDENTS);
        return new FamilyContactDto.InboxSummary(
                reportRepository.countBySchoolIdAndStatus(schoolId, AbsenceReportStatus.PENDING),
                conversationRepository.countBySchoolIdAndUnreadBySchoolTrue(schoolId));
    }

    // ------------------------------------------------------------------ absences signalées

    @Transactional(readOnly = true)
    public List<FamilyContactDto.AbsenceReportItem> absenceReports(Long schoolId) {
        guard.requireSchoolModule(schoolId, StaffModule.STUDENTS);
        return reportRepository.findBySchool(schoolId).stream()
                .filter(r -> r.getStatus() != AbsenceReportStatus.CANCELLED)
                .map(r -> FamilyContactMapper.report(r, 0))
                .toList();
    }

    /** Accepte le signalement et justifie les absences / retards déjà saisis sur la période. */
    public FamilyContactDto.AbsenceReportItem acknowledge(Long reportId, FamilyContactDto.AbsenceDecision decision) {
        AbsenceReport report = requirePending(reportId);
        decide(report, AbsenceReportStatus.ACKNOWLEDGED, decision == null ? null : decision.comment());
        int justified = 0;
        String justification = AbsenceReportJustification.of(report.getReason());
        for (Attendance attendance : attendanceRepository
                .findByStudentIdOrderByAttendanceDateDesc(report.getStudent().getId())) {
            boolean inRange = !attendance.getAttendanceDate().isBefore(report.getStartDate())
                    && !attendance.getAttendanceDate().isAfter(report.getEndDate());
            boolean missed = attendance.getStatus() == AttendanceStatus.ABSENT
                    || attendance.getStatus() == AttendanceStatus.LATE;
            if (inRange && missed && (attendance.getJustification() == null
                    || attendance.getJustification().isBlank())) {
                attendance.setJustification(justification);
                attendanceRepository.save(attendance);
                justified++;
            }
        }
        return FamilyContactMapper.report(report, justified);
    }

    public FamilyContactDto.AbsenceReportItem reject(Long reportId, FamilyContactDto.AbsenceDecision decision) {
        String comment = decision == null ? null : decision.comment();
        if (comment == null || comment.isBlank()) {
            throw new IllegalArgumentException("Indiquez au parent le motif du refus");
        }
        AbsenceReport report = requirePending(reportId);
        decide(report, AbsenceReportStatus.REJECTED, comment);
        return FamilyContactMapper.report(report, 0);
    }

    // ------------------------------------------------------------------ messagerie

    @Transactional(readOnly = true)
    public List<FamilyContactDto.ConversationSummary> conversations(Long schoolId) {
        guard.requireSchoolModule(schoolId, StaffModule.STUDENTS);
        return conversationRepository.findBySchool(schoolId).stream()
                .map(c -> FamilyContactMapper.summary(c, true))
                .toList();
    }

    public FamilyContactDto.ConversationThread thread(Long conversationId) {
        SchoolConversation conversation = requireConversation(conversationId);
        conversation.setUnreadBySchool(false);
        return FamilyContactMapper.thread(conversation, messageRepository.findThread(conversationId), true);
    }

    public FamilyContactDto.ConversationThread reply(Long conversationId, FamilyContactDto.ReplyRequest request) {
        SchoolConversation conversation = requireConversation(conversationId);
        LocalDateTime now = LocalDateTime.now();
        messageRepository.save(SchoolConversationMessage.builder()
                .conversation(conversation)
                .sender(currentUser())
                .fromSchool(true)
                .content(request.content().trim())
                .sentAt(now)
                .build());
        conversation.setLastMessageAt(now);
        conversation.setUnreadByParent(true);
        conversation.setUnreadBySchool(false);
        return FamilyContactMapper.thread(conversation, messageRepository.findThread(conversationId), true);
    }

    // ------------------------------------------------------------------ utilitaires

    private AbsenceReport requirePending(Long reportId) {
        AbsenceReport report = reportRepository.findById(reportId)
                .orElseThrow(() -> new IllegalArgumentException("Signalement introuvable : " + reportId));
        guard.requireSchoolModule(report.getSchool().getId(), StaffModule.STUDENTS);
        if (report.getStatus() != AbsenceReportStatus.PENDING) {
            throw new IllegalArgumentException("Ce signalement a déjà été traité ou annulé");
        }
        return report;
    }

    private void decide(AbsenceReport report, AbsenceReportStatus status, String comment) {
        report.setStatus(status);
        report.setSchoolComment(comment == null || comment.isBlank() ? null : comment.trim());
        report.setHandledBy(currentUser());
        report.setHandledAt(LocalDateTime.now());
    }

    private SchoolConversation requireConversation(Long conversationId) {
        SchoolConversation conversation = conversationRepository.findById(conversationId)
                .orElseThrow(() -> new IllegalArgumentException("Conversation introuvable : " + conversationId));
        guard.requireSchoolModule(conversation.getSchool().getId(), StaffModule.STUDENTS);
        return conversation;
    }

    private User currentUser() {
        return userRepository.getReferenceById(guard.currentUserId());
    }
}