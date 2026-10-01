package org.afritechinnovations.service.communication;

import org.afritechinnovations.dto.communication.FamilyContactDto;
import org.afritechinnovations.model.common.User;
import org.afritechinnovations.model.communication.AbsenceReport;
import org.afritechinnovations.model.communication.SchoolConversation;
import org.afritechinnovations.model.communication.SchoolConversationMessage;
import org.afritechinnovations.model.people.Student;

import java.util.List;

/** Conversions communes aux vues parent et établissement. */
final class FamilyContactMapper {

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

    static FamilyContactDto.AbsenceReportItem report(AbsenceReport r, int justified) {
        Student s = r.getStudent();
        return new FamilyContactDto.AbsenceReportItem(r.getId(), s.getId(), name(s), s.getRegistrationNumber(),
                r.getStartDate(), r.getEndDate(), r.getReason(), r.getStatus(), name(r.getReportedBy()),
                r.getReportedBy() == null ? null : r.getReportedBy().getPhone(), r.getSchoolComment(),
                name(r.getHandledBy()), r.getHandledAt(), r.getCreatedAt(), justified);
    }

    static FamilyContactDto.ConversationSummary summary(SchoolConversation c, boolean forSchool) {
        User parent = c.getParentUser();
        Student student = c.getStudent();
        return new FamilyContactDto.ConversationSummary(c.getId(), c.getSchool().getId(), c.getSchool().getName(),
                parent.getId(), name(parent), parent.getPhone(), parent.getEmail(),
                student == null ? null : student.getId(), name(student), c.getSubject(), c.getCreatedAt(),
                c.getLastMessageAt(), forSchool ? c.isUnreadBySchool() : c.isUnreadByParent());
    }

    static FamilyContactDto.ConversationThread thread(SchoolConversation c, List<SchoolConversationMessage> messages,
                                                      boolean forSchool) {
        return new FamilyContactDto.ConversationThread(summary(c, forSchool), messages.stream()
                .map(m -> new FamilyContactDto.ConversationMessage(m.getId(), m.isFromSchool(),
                        m.isFromSchool() ? (name(m.getSender()) == null ? "L'établissement"
                                : name(m.getSender()) + " (établissement)") : name(m.getSender()),
                        m.getContent(), m.getSentAt()))
                .toList());
    }
}