package org.afritechinnovations.service.communication;

import lombok.RequiredArgsConstructor;
import org.afritechinnovations.dto.communication.FamilyContactDto;
import org.afritechinnovations.model.academic.Attendance;
import org.afritechinnovations.model.academic.AttendanceStatus;
import org.afritechinnovations.model.common.StaffModule;
import org.afritechinnovations.model.common.User;
import org.afritechinnovations.model.communication.AbsenceReport;
import org.afritechinnovations.model.communication.AbsenceReportStatus;
import org.afritechinnovations.model.communication.FamilyAttendanceType;
import org.afritechinnovations.model.people.EnrollmentStatus;
import org.afritechinnovations.model.communication.SchoolConversation;
import org.afritechinnovations.model.communication.SchoolConversationMessage;
import org.afritechinnovations.model.communication.ConversationParticipant;
import org.afritechinnovations.repository.academic.AttendanceRepository;
import org.afritechinnovations.repository.common.UserRepository;
import org.afritechinnovations.repository.people.StudentEnrollmentRepository;
import org.afritechinnovations.repository.communication.AbsenceReportRepository;
import org.afritechinnovations.repository.communication.SchoolConversationMessageRepository;
import org.afritechinnovations.repository.communication.SchoolConversationRepository;
import org.afritechinnovations.repository.communication.ConversationParticipantRepository;
import org.afritechinnovations.security.AccessGuard;
import org.afritechinnovations.service.self.TeacherSpaceService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
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
    private final ConversationParticipantRepository participantRepository;
    private final UserRepository userRepository;
    private final TeacherSpaceService teacherSpace;
    private final StudentEnrollmentRepository enrollmentRepository;

    @Transactional(readOnly = true)
    public FamilyContactDto.InboxSummary summary(Long schoolId) {
        guard.requireSchoolModule(schoolId, StaffModule.STUDENTS);
        return new FamilyContactDto.InboxSummary(
                reportRepository.countBySchoolIdAndStatus(schoolId, AbsenceReportStatus.PENDING),
                messageRepository.countUnreadBySchool(schoolId));
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

    public FamilyContactDto.AbsenceReportItem record(Long reportId) {
        AbsenceReport report = requirePending(reportId);
        if (!report.getStartDate().equals(report.getEndDate())) {
            throw new IllegalArgumentException("Ce signalement concerne plusieurs jours ; saisissez les présences depuis la fiche de l'élève");
        }
        var date = report.getStartDate();
        if (date.isAfter(LocalDate.now())) {
            throw new IllegalArgumentException("Une présence ne peut pas être enregistrée pour une date future");
        }
        var enrollments = enrollmentRepository.findByStudentId(report.getStudent().getId()).stream()
                .filter(enrollment -> enrollment.getStatus() == EnrollmentStatus.ACTIVE)
                .filter(enrollment -> enrollment.getSchoolClass().getSchool().getId().equals(report.getSchool().getId()))
                .filter(enrollment -> !date.isBefore(enrollment.getSchoolClass().getAcademicYear().getStartDate())
                        && !date.isAfter(enrollment.getSchoolClass().getAcademicYear().getEndDate()))
                .toList();
        if (enrollments.size() != 1) {
            throw new IllegalArgumentException("Impossible d'identifier une classe active unique pour cet élève à cette date ; consultez sa fiche");
        }
        var schoolClass = enrollments.getFirst().getSchoolClass();
        Attendance attendance = attendanceRepository.findByStudentIdAndSchoolClassIdAndAttendanceDate(
                report.getStudent().getId(), schoolClass.getId(), date).orElseGet(() -> Attendance.builder()
                .student(report.getStudent()).schoolClass(schoolClass).attendanceDate(date).build());
        attendance.setStatus(report.getAttendanceType() == FamilyAttendanceType.LATE
                ? AttendanceStatus.LATE : AttendanceStatus.ABSENT);
        attendance.setJustification(report.getReason());
        attendanceRepository.save(attendance);
        decide(report, AbsenceReportStatus.ACKNOWLEDGED, "Enregistré par l'administration.");
        return FamilyContactMapper.report(report, 1);
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
                .filter(c -> c.getParticipants().stream().anyMatch(p -> p.getSchool() != null))
                .map(c -> FamilyContactMapper.summary(c, true))
                .toList();
    }

    public FamilyContactDto.ConversationThread thread(Long conversationId) {
        SchoolConversation conversation = requireConversation(conversationId);
        conversation.setUnreadBySchool(false);
        markSchoolRead(conversation, LocalDateTime.now());
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

    @Transactional(readOnly = true)
    public List<FamilyContactDto.ConversationSummary> teacherConversations(Long userId, Long classId) {
        List<Long> studentIds = teacherSpace.students(userId, classId).stream()
                .map(org.afritechinnovations.dto.self.SelfServiceDto.RosterStudent::studentId).toList();
        if (studentIds.isEmpty()) return List.of();
        Long schoolId = teacherSpace.classes(userId).stream().filter(c -> c.classId().equals(classId))
                .map(org.afritechinnovations.dto.self.SelfServiceDto.TeacherClass::schoolId).findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Classe introuvable : " + classId));
        return conversationRepository.findBySchool(schoolId).stream()
                .filter(c -> c.getStudent() != null && studentIds.contains(c.getStudent().getId()))
                .filter(c -> participantRepository.findByConversationIdAndSchoolId(c.getId(), schoolId).isPresent()
                        || participantRepository.findByConversationIdAndUserId(c.getId(), userId).isPresent())
                .map(c -> participantRepository.findByConversationIdAndUserId(c.getId(), userId).isPresent()
                        ? FamilyContactMapper.summaryForUser(c, userId) : FamilyContactMapper.summary(c, true)).toList();
    }

    public FamilyContactDto.ConversationThread teacherThread(Long userId, Long classId, Long conversationId) {
        SchoolConversation conversation = requireTeacherConversation(userId, classId, conversationId);
        boolean personalParticipant = participantRepository.findByConversationIdAndUserId(conversationId, userId).isPresent();
        if (!personalParticipant) {
            conversation.setUnreadBySchool(false);
            markSchoolRead(conversation, LocalDateTime.now());
        }
        conversation.getParticipants().stream().filter(p -> p.getUser() != null && p.getUser().getId().equals(userId))
                .forEach(p -> p.setLastReadAt(LocalDateTime.now()));
        return participantRepository.findByConversationIdAndUserId(conversationId, userId).isPresent()
                ? FamilyContactMapper.threadForUser(conversation, messageRepository.findThread(conversationId), userId)
                : FamilyContactMapper.thread(conversation, messageRepository.findThread(conversationId), true);
    }

    public FamilyContactDto.ConversationThread teacherReply(Long userId, Long classId, Long conversationId,
                                                              FamilyContactDto.ReplyRequest request) {
        SchoolConversation conversation = requireTeacherConversation(userId, classId, conversationId);
        LocalDateTime now = LocalDateTime.now();
        boolean personalParticipant = participantRepository.findByConversationIdAndUserId(conversationId, userId).isPresent();
        messageRepository.save(SchoolConversationMessage.builder()
                .conversation(conversation)
                .sender(currentUser())
                .fromSchool(!personalParticipant)
                .content(request.content().trim())
                .sentAt(now)
                .build());
        conversation.setLastMessageAt(now);
        conversation.setUnreadByParent(!personalParticipant);
        conversation.setUnreadBySchool(personalParticipant);
        conversation.getParticipants().stream().filter(p -> p.getUser() != null && p.getUser().getId().equals(userId))
                .forEach(p -> p.setLastReadAt(now));
        if (!personalParticipant) markSchoolRead(conversation, now);
        return personalParticipant
                ? FamilyContactMapper.threadForUser(conversation, messageRepository.findThread(conversationId), userId)
                : FamilyContactMapper.thread(conversation, messageRepository.findThread(conversationId), true);
    }

    // ------------------------------------------------------------------ utilitaires

    private AbsenceReport requirePending(Long reportId) {
        AbsenceReport report = reportRepository.findByIdForUpdate(reportId)
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
        if (!participantRepository.findByConversationIdAndSchoolId(conversationId, conversation.getSchool().getId()).isPresent()) {
            throw new org.springframework.security.access.AccessDeniedException("Cette conversation ne concerne pas l'établissement");
        }
        return conversation;
    }

    private SchoolConversation requireTeacherConversation(Long userId, Long classId, Long conversationId) {
        List<Long> studentIds = teacherSpace.students(userId, classId).stream()
                .map(org.afritechinnovations.dto.self.SelfServiceDto.RosterStudent::studentId).toList();
        SchoolConversation conversation = conversationRepository.findById(conversationId)
                .orElseThrow(() -> new IllegalArgumentException("Conversation introuvable : " + conversationId));
        if (conversation.getStudent() == null || !studentIds.contains(conversation.getStudent().getId())) {
            throw new org.springframework.security.access.AccessDeniedException(
                    "Cette conversation ne concerne pas un élève de votre classe");
        }
        boolean schoolParticipant = participantRepository.findByConversationIdAndSchoolId(conversationId,
                conversation.getSchool().getId()).isPresent();
        boolean userParticipant = participantRepository.findByConversationIdAndUserId(conversationId, userId).isPresent();
        if (!schoolParticipant && !userParticipant) {
            throw new org.springframework.security.access.AccessDeniedException("Vous n'êtes pas destinataire de cette conversation");
        }
        return conversation;
    }

    private User currentUser() {
        return userRepository.getReferenceById(guard.currentUserId());
    }

    private void markSchoolRead(SchoolConversation conversation, LocalDateTime at) {
        conversation.getParticipants().stream()
                .filter(p -> p.getSchool() != null && p.getSchool().getId().equals(conversation.getSchool().getId()))
                .forEach(p -> p.setLastReadAt(at));
    }
}
