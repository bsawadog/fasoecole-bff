package org.afritechinnovations.service.communication;

import org.afritechinnovations.dto.communication.FamilyContactDto;
import org.afritechinnovations.model.common.User;
import org.afritechinnovations.model.communication.AbsenceReport;
import org.afritechinnovations.model.communication.SchoolConversation;
import org.afritechinnovations.model.communication.SchoolConversationMessage;
import org.afritechinnovations.model.communication.ConversationParticipant;
import org.afritechinnovations.model.people.Student;

import java.util.List;
import java.util.Comparator;

/** Conversions communes aux vues parent et établissement. */
public final class FamilyContactMapper {

    private FamilyContactMapper() {
    }

    static String name(User user) {
        if (user == null) {
            return null;
        }
        String first = user.getFirstName() == null ? "" : user.getFirstName();
        String last = user.getLastName() == null ? "" : user.getLastName();
        return (first + " " + last).trim();
    }

    static String name(Student student) {
        return student == null ? null : name(student.getUser());
    }

    public static FamilyContactDto.AbsenceReportItem report(AbsenceReport r, int justified) {
        Student s = r.getStudent();
        return new FamilyContactDto.AbsenceReportItem(r.getId(), s.getId(), name(s), s.getRegistrationNumber(),
                r.getStartDate(), r.getEndDate(), r.getAttendanceType(), r.getReason(), r.getStatus(), name(r.getReportedBy()),
                r.getReportedBy() == null ? null : r.getReportedBy().getPhone(), r.getSchoolComment(),
                name(r.getHandledBy()), r.getHandledAt(), r.getCreatedAt(), justified);
    }

    static FamilyContactDto.ConversationSummary summary(SchoolConversation c, boolean forSchool) {
        User parent = c.getParentUser();
        Student student = c.getStudent();
        List<String> recipients = c.getParticipants().stream()
                .map(p -> p.getSchool() != null ? p.getSchool().getName() : name(p.getUser()))
                .filter(n -> n != null && !n.isBlank()).distinct().toList();
        ConversationParticipant participant = c.getParticipants().stream()
                .filter(p -> forSchool ? p.getSchool() != null
                        : p.getUser() != null && parent != null && p.getUser().getId().equals(parent.getId()))
                .findFirst().orElse(null);
        boolean unread = participant == null
                ? (forSchool ? c.isUnreadBySchool() : c.isUnreadByParent())
                : participant.getLastReadAt() == null || participant.getLastReadAt().isBefore(c.getLastMessageAt());
        return new FamilyContactDto.ConversationSummary(c.getId(), c.getSchool().getId(), c.getSchool().getName(),
                parent == null ? null : parent.getId(), name(parent), parent == null ? null : parent.getPhone(),
                parent == null ? null : parent.getEmail(),
                student == null ? null : student.getId(), name(student), c.getSubject(), c.getCreatedAt(),
                c.getLastMessageAt(), unread, recipients, (int) c.getParticipants().stream()
                        .filter(p -> forSchool ? p.getUser() != null
                                : p.getSchool() != null || (p.getUser() != null && (parent == null || !p.getUser().getId().equals(parent.getId()))))
                        .count());
    }

    static FamilyContactDto.ConversationSummary summaryForUser(SchoolConversation c, Long userId) {
        FamilyContactDto.ConversationSummary summary = summary(c, false);
        ConversationParticipant participant = c.getParticipants().stream()
                .filter(p -> p.getUser() != null && p.getUser().getId().equals(userId)).findFirst().orElse(null);
        boolean unread = participant == null || participant.getLastReadAt() == null
                || participant.getLastReadAt().isBefore(c.getLastMessageAt());
        return new FamilyContactDto.ConversationSummary(summary.id(), summary.schoolId(), summary.schoolName(),
                summary.parentUserId(), summary.parentName(), summary.parentPhone(), summary.parentEmail(),
                summary.studentId(), summary.studentName(), summary.subject(), summary.createdAt(),
                summary.lastMessageAt(), unread, summary.recipientNames(), (int) c.getParticipants().stream()
                        .filter(p -> p.getSchool() != null || (p.getUser() != null && !p.getUser().getId().equals(userId)))
                        .count());
    }

    static FamilyContactDto.ConversationThread thread(SchoolConversation c, List<SchoolConversationMessage> messages,
                                                      boolean forSchool) {
        return new FamilyContactDto.ConversationThread(summary(c, forSchool), messages.stream()
                .map(m -> new FamilyContactDto.ConversationMessage(m.getId(), m.isFromSchool(),
                        m.getSender() == null ? null : m.getSender().getId(), forSchool ? m.isFromSchool() : !m.isFromSchool(),
                        m.isFromSchool() ? (name(m.getSender()) == null ? "L'établissement"
                                : name(m.getSender()) + " (établissement)") : name(m.getSender()),
                        m.getContent(), m.getSentAt(), readers(c, m), attachments(m)))
                .toList());
    }

    static FamilyContactDto.ConversationThread threadForUser(SchoolConversation c, List<SchoolConversationMessage> messages,
                                                             Long userId) {
        FamilyContactDto.ConversationSummary summary = summaryForUser(c, userId);
        List<FamilyContactDto.ConversationMessage> mapped = messages.stream().map(m ->
                new FamilyContactDto.ConversationMessage(m.getId(), m.isFromSchool(),
                        m.getSender() == null ? null : m.getSender().getId(), m.getSender() != null && m.getSender().getId().equals(userId),
                        name(m.getSender()),
                        m.getContent(), m.getSentAt(), readers(c, m), attachments(m))).toList();
        return new FamilyContactDto.ConversationThread(summary, mapped);
    }

    private static List<FamilyContactDto.Attachment> attachments(SchoolConversationMessage message) {
        return message.getAttachments().stream()
                .map(a -> new FamilyContactDto.Attachment(a.getId(), a.getFilename(), a.getSizeBytes())).toList();
    }

    private static List<String> readers(SchoolConversation c, SchoolConversationMessage message) {
        return c.getParticipants().stream()
                .filter(p -> p.getLastReadAt() != null && !p.getLastReadAt().isBefore(message.getSentAt()))
                .filter(p -> p.getUser() == null || message.getSender() == null
                        || !p.getUser().getId().equals(message.getSender().getId()))
                .filter(p -> !message.isFromSchool() || p.getSchool() == null)
                .map(p -> p.getSchool() != null ? p.getSchool().getName() : name(p.getUser()))
                .filter(n -> n != null && !n.isBlank()).distinct().sorted(Comparator.naturalOrder()).toList();
    }
}
