package org.afritechinnovations.dto.communication;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.afritechinnovations.model.communication.AbsenceReportStatus;
import org.afritechinnovations.model.communication.FamilyAttendanceType;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/** Signalements d'absence et échanges parent ↔ établissement. */
public final class FamilyContactDto {

    private FamilyContactDto() {
    }

    // ------------------------------------------------------------------ absences signalées

    public record AbsenceReportRequest(
            @NotNull(message = "La date de début est obligatoire") LocalDate startDate,
            @NotNull(message = "La date de fin est obligatoire") LocalDate endDate,
            @NotNull(message = "Le type de signalement est obligatoire") FamilyAttendanceType attendanceType,
            @NotBlank(message = "Le motif est obligatoire")
            @Size(max = 500, message = "Le motif ne doit pas dépasser 500 caractères") String reason) {
        public AbsenceReportRequest(LocalDate startDate, LocalDate endDate, String reason) {
            this(startDate, endDate, FamilyAttendanceType.ABSENT, reason);
        }
    }

    public record AbsenceDecision(
            @Size(max = 500, message = "Le commentaire ne doit pas dépasser 500 caractères") String comment) {
    }

    public record AbsenceReportItem(Long id, Long studentId, String studentName, String registrationNumber,
                                    LocalDate startDate, LocalDate endDate, FamilyAttendanceType attendanceType, String reason,
                                    AbsenceReportStatus status, String reportedByName, String reportedByPhone,
                                    String schoolComment, String handledByName, LocalDateTime handledAt,
                                    LocalDateTime createdAt, int justifiedAttendances) {
    }

    // ------------------------------------------------------------------ messagerie

    public record NewConversationRequest(
            @NotNull(message = "L'établissement est obligatoire") Long schoolId,
            Long studentId,
            @NotBlank(message = "L'objet est obligatoire")
            @Size(max = 160, message = "L'objet ne doit pas dépasser 160 caractères") String subject,
            @NotBlank(message = "Le message est obligatoire")
            @Size(max = 4000, message = "Le message ne doit pas dépasser 4000 caractères") String content,
            List<Long> recipientUserIds,
            boolean recipientSchool) {
        public NewConversationRequest(Long schoolId, Long studentId, String subject, String content) {
            this(schoolId, studentId, subject, content, List.of(), true);
        }
    }

    public record TeacherMessageRequest(
            @NotNull Long schoolId,
            Long classId,
            @NotBlank @Size(max = 160) String subject,
            @NotNull @Size(max = 4000) String content,
            @NotNull @Size(min = 1, max = 1000) List<@NotNull Long> recipientUserIds) { }

    public record ReplyRequest(
            @NotBlank(message = "Le message est obligatoire")
            @Size(max = 4000, message = "Le message ne doit pas dépasser 4000 caractères") String content) {
    }

    public record Recipient(Long userId, String fullName, String role, String email) {
        public Recipient(Long userId, String fullName, String role) { this(userId, fullName, role, null); }
    }

    public record ConversationRecipientsRequest(@NotNull Long schoolId, Long studentId) { }

    public record ConversationSummary(Long id, Long schoolId, String schoolName, Long parentUserId, String parentName,
                                      String parentPhone, String parentEmail, Long studentId, String studentName,
                                      String subject, LocalDateTime createdAt, LocalDateTime lastMessageAt,
                                      boolean unread, List<String> recipientNames, int recipientCount) {
        public ConversationSummary(Long id, Long schoolId, String schoolName, Long parentUserId, String parentName,
                                   String parentPhone, String parentEmail, Long studentId, String studentName,
                                   String subject, LocalDateTime createdAt, LocalDateTime lastMessageAt,
                                   boolean unread, List<String> recipientNames) {
            this(id, schoolId, schoolName, parentUserId, parentName, parentPhone, parentEmail, studentId, studentName,
                    subject, createdAt, lastMessageAt, unread, recipientNames, recipientNames == null ? 0 : recipientNames.size());
        }
    }

    public record ConversationMessage(Long id, boolean fromSchool, Long senderId, boolean mine, String senderName,
                                      String content, LocalDateTime sentAt, List<String> readBy, List<Attachment> attachments) {
        public ConversationMessage(Long id, boolean fromSchool, Long senderId, boolean mine, String senderName,
                                   String content, LocalDateTime sentAt, List<String> readBy) {
            this(id, fromSchool, senderId, mine, senderName, content, sentAt, readBy, List.of());
        }
    }

    public record Attachment(Long id, String filename, long sizeBytes) { }

    public record ConversationThread(ConversationSummary conversation, List<ConversationMessage> messages) {
    }

    public record InboxSummary(long pendingAbsenceReports, long unreadMessages) {
    }
}
