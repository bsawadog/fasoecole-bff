package org.afritechinnovations.dto.communication;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.afritechinnovations.model.communication.AbsenceReportStatus;

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
            @NotBlank(message = "Le motif est obligatoire")
            @Size(max = 500, message = "Le motif ne doit pas dépasser 500 caractères") String reason) {
    }

    public record AbsenceDecision(
            @Size(max = 500, message = "Le commentaire ne doit pas dépasser 500 caractères") String comment) {
    }

    public record AbsenceReportItem(Long id, Long studentId, String studentName, String registrationNumber,
                                    LocalDate startDate, LocalDate endDate, String reason,
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
            @Size(max = 4000, message = "Le message ne doit pas dépasser 4000 caractères") String content) {
    }

    public record ReplyRequest(
            @NotBlank(message = "Le message est obligatoire")
            @Size(max = 4000, message = "Le message ne doit pas dépasser 4000 caractères") String content) {
    }

    public record ConversationSummary(Long id, Long schoolId, String schoolName, Long parentUserId, String parentName,
                                      String parentPhone, String parentEmail, Long studentId, String studentName,
                                      String subject, LocalDateTime createdAt, LocalDateTime lastMessageAt,
                                      boolean unread) {
    }

    public record ConversationMessage(Long id, boolean fromSchool, String senderName, String content,
                                      LocalDateTime sentAt) {
    }

    public record ConversationThread(ConversationSummary conversation, List<ConversationMessage> messages) {
    }

    public record InboxSummary(long pendingAbsenceReports, long unreadConversations) {
    }
}