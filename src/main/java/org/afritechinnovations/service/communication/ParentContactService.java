package org.afritechinnovations.service.communication;

import lombok.RequiredArgsConstructor;
import org.afritechinnovations.dto.communication.FamilyContactDto;
import org.afritechinnovations.dto.self.SelfServiceDto;
import org.afritechinnovations.model.common.School;
import org.afritechinnovations.model.common.User;
import org.afritechinnovations.model.communication.AbsenceReport;
import org.afritechinnovations.model.communication.AbsenceReportStatus;
import org.afritechinnovations.model.communication.SchoolConversation;
import org.afritechinnovations.model.communication.SchoolConversationMessage;
import org.afritechinnovations.model.communication.ConversationParticipant;
import org.afritechinnovations.model.people.Student;
import org.afritechinnovations.repository.common.SchoolRepository;
import org.afritechinnovations.repository.common.UserRepository;
import org.afritechinnovations.repository.communication.AbsenceReportRepository;
import org.afritechinnovations.repository.communication.SchoolConversationMessageRepository;
import org.afritechinnovations.repository.communication.SchoolConversationRepository;
import org.afritechinnovations.repository.communication.ConversationParticipantRepository;
import org.afritechinnovations.service.self.FamilySpaceService;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;

/** Côté parent : signaler une absence et écrire à l'établissement de ses enfants. */
@Service
@RequiredArgsConstructor
@Transactional
public class ParentContactService {

    static final int MAX_PAST_DAYS = 30;
    static final int MAX_FUTURE_DAYS = 180;
    static final int MAX_LENGTH_DAYS = 60;

    private final FamilySpaceService familySpace;
    private final AbsenceReportRepository reportRepository;
    private final SchoolConversationRepository conversationRepository;
    private final SchoolConversationMessageRepository messageRepository;
    private final ConversationParticipantRepository participantRepository;
    private final SchoolRepository schoolRepository;
    private final UserRepository userRepository;

    // ------------------------------------------------------------------ absences

    @Transactional(readOnly = true)
    public List<FamilyContactDto.AbsenceReportItem> absenceReports(Long userId, Long studentId) {
        Student child = familySpace.requireGuardedChild(userId, studentId);
        return reportRepository.findByStudent(child.getId()).stream()
                .map(r -> FamilyContactMapper.report(r, 0))
                .toList();
    }

    public FamilyContactDto.AbsenceReportItem reportAbsence(Long userId, Long studentId,
                                                            FamilyContactDto.AbsenceReportRequest request) {
        Student child = familySpace.requireGuardedChild(userId, studentId);
        LocalDate today = LocalDate.now();
        LocalDate start = request.startDate();
        LocalDate end = request.endDate();
        if (end.isBefore(start)) {
            throw new IllegalArgumentException("La date de fin doit être postérieure ou égale à la date de début");
        }
        if (start.isBefore(today.minusDays(MAX_PAST_DAYS))) {
            throw new IllegalArgumentException("Une absence ne peut être signalée plus de " + MAX_PAST_DAYS
                    + " jours après les faits ; contactez l'établissement");
        }
        if (start.isAfter(today.plusDays(MAX_FUTURE_DAYS))) {
            throw new IllegalArgumentException("Une absence ne peut être signalée plus de " + MAX_FUTURE_DAYS
                    + " jours à l'avance");
        }
        if (ChronoUnit.DAYS.between(start, end) + 1 > MAX_LENGTH_DAYS) {
            throw new IllegalArgumentException("Une absence signalée ne peut pas dépasser " + MAX_LENGTH_DAYS
                    + " jours ; contactez l'établissement");
        }
        AbsenceReport report = reportRepository.save(AbsenceReport.builder()
                .student(child)
                .school(child.getSchool())
                .reportedBy(userRepository.getReferenceById(userId))
                .startDate(start)
                .endDate(end)
                .attendanceType(request.attendanceType())
                .reason(request.reason().trim())
                .status(AbsenceReportStatus.PENDING)
                .build());
        return FamilyContactMapper.report(report, 0);
    }

    public FamilyContactDto.AbsenceReportItem cancelReport(Long userId, Long reportId) {
        AbsenceReport report = reportRepository.findById(reportId)
                .orElseThrow(() -> new IllegalArgumentException("Signalement introuvable : " + reportId));
        familySpace.requireGuardedChild(userId, report.getStudent().getId());
        if (report.getStatus() != AbsenceReportStatus.PENDING) {
            throw new IllegalArgumentException("Seul un signalement en attente peut être annulé");
        }
        report.setStatus(AbsenceReportStatus.CANCELLED);
        return FamilyContactMapper.report(report, 0);
    }

    // ------------------------------------------------------------------ messagerie

    @Transactional(readOnly = true)
    public List<SelfServiceDto.SchoolContact> schools(Long userId) {
        return familySpace.parentSchools(userId);
    }

    @Transactional(readOnly = true)
    public List<FamilyContactDto.ConversationSummary> conversations(Long userId) {
        return conversationRepository.findByParentUser(userId).stream()
                .map(c -> FamilyContactMapper.summary(c, false))
                .toList();
    }

    public FamilyContactDto.ConversationThread start(Long userId, FamilyContactDto.NewConversationRequest request) {
        requireParentSchool(userId, request.schoolId());
        Student child = null;
        if (request.studentId() != null) {
            child = familySpace.requireGuardedChild(userId, request.studentId());
            if (!child.getSchool().getId().equals(request.schoolId())) {
                throw new IllegalArgumentException("Cet enfant n'est pas inscrit dans cet établissement");
            }
        }
        School school = schoolRepository.findById(request.schoolId())
                .orElseThrow(() -> new IllegalArgumentException("Établissement introuvable : " + request.schoolId()));
        User parent = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("Utilisateur introuvable : " + userId));
        LocalDateTime now = LocalDateTime.now();
        SchoolConversation conversation = conversationRepository.save(SchoolConversation.builder()
                .school(school)
                .parentUser(parent)
                .student(child)
                .subject(request.subject().trim())
                .createdAt(now)
                .lastMessageAt(now)
                .unreadBySchool(true)
                .unreadByParent(false)
                .build());
        participantRepository.saveAll(List.of(
                ConversationParticipant.builder().conversation(conversation).user(parent).lastReadAt(now).build(),
                ConversationParticipant.builder().conversation(conversation).school(school).build()));
        messageRepository.save(SchoolConversationMessage.builder()
                .conversation(conversation)
                .sender(parent)
                .fromSchool(false)
                .content(request.content().trim())
                .sentAt(now)
                .build());
        return FamilyContactMapper.thread(conversation, messageRepository.findThread(conversation.getId()), false);
    }

    public FamilyContactDto.ConversationThread thread(Long userId, Long conversationId) {
        SchoolConversation conversation = requireOwnConversation(userId, conversationId);
        conversation.setUnreadByParent(false);
        conversation.getParticipants().stream().filter(p -> p.getUser() != null && p.getUser().getId().equals(userId))
                .forEach(p -> p.setLastReadAt(LocalDateTime.now()));
        return FamilyContactMapper.thread(conversation, messageRepository.findThread(conversationId), false);
    }

    public FamilyContactDto.ConversationThread reply(Long userId, Long conversationId,
                                                     FamilyContactDto.ReplyRequest request) {
        SchoolConversation conversation = requireOwnConversation(userId, conversationId);
        requireParentSchool(userId, conversation.getSchool().getId());
        LocalDateTime now = LocalDateTime.now();
        messageRepository.save(SchoolConversationMessage.builder()
                .conversation(conversation)
                .sender(conversation.getParentUser())
                .fromSchool(false)
                .content(request.content().trim())
                .sentAt(now)
                .build());
        conversation.setLastMessageAt(now);
        conversation.setUnreadBySchool(true);
        conversation.setUnreadByParent(false);
        conversation.getParticipants().stream().filter(p -> p.getUser() != null && p.getUser().getId().equals(userId))
                .forEach(p -> p.setLastReadAt(now));
        return FamilyContactMapper.thread(conversation, messageRepository.findThread(conversationId), false);
    }

    // ------------------------------------------------------------------ accès

    private void requireParentSchool(Long userId, Long schoolId) {
        boolean allowed = familySpace.parentSchools(userId).stream().anyMatch(s -> s.schoolId().equals(schoolId));
        if (!allowed) {
            throw new AccessDeniedException("Vous n'avez pas d'accès parent actif dans cet établissement");
        }
    }

    private SchoolConversation requireOwnConversation(Long userId, Long conversationId) {
        SchoolConversation conversation = conversationRepository.findById(conversationId)
                .orElseThrow(() -> new IllegalArgumentException("Conversation introuvable : " + conversationId));
        if (!conversation.getParentUser().getId().equals(userId)) {
            throw new AccessDeniedException("Cette conversation ne vous appartient pas");
        }
        return conversation;
    }
}
