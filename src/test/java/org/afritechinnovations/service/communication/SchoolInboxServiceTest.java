package org.afritechinnovations.service.communication;

import org.afritechinnovations.dto.communication.FamilyContactDto;
import org.afritechinnovations.model.academic.Attendance;
import org.afritechinnovations.model.academic.AttendanceStatus;
import org.afritechinnovations.model.common.School;
import org.afritechinnovations.model.common.StaffModule;
import org.afritechinnovations.model.common.User;
import org.afritechinnovations.model.communication.AbsenceReport;
import org.afritechinnovations.model.communication.AbsenceReportStatus;
import org.afritechinnovations.model.communication.ConversationParticipant;
import org.afritechinnovations.model.communication.SchoolConversation;
import org.afritechinnovations.model.people.Student;
import org.afritechinnovations.repository.academic.AttendanceRepository;
import org.afritechinnovations.repository.common.UserRepository;
import org.afritechinnovations.repository.communication.AbsenceReportRepository;
import org.afritechinnovations.repository.communication.ConversationParticipantRepository;
import org.afritechinnovations.repository.communication.SchoolConversationMessageRepository;
import org.afritechinnovations.repository.communication.SchoolConversationRepository;
import org.afritechinnovations.security.AccessGuard;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.security.access.AccessDeniedException;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class SchoolInboxServiceTest {
    @Mock AccessGuard guard;
    @Mock AbsenceReportRepository reports;
    @Mock AttendanceRepository attendances;
    @Mock SchoolConversationRepository conversations;
    @Mock SchoolConversationMessageRepository messages;
    @Mock ConversationParticipantRepository participants;
    @Mock UserRepository users;
    @InjectMocks SchoolInboxService service;

    private final School school = School.builder().id(1L).name("École A").build();
    private final User staff = User.builder().id(30L).firstName("Ali").lastName("Zongo").build();
    private final Student child = Student.builder().id(11L).school(school).registrationNumber("MAT-11")
            .user(User.builder().id(111L).firstName("Moussa").lastName("Kaboré").build()).build();
    private final LocalDate monday = LocalDate.of(2026, 3, 2);

    @BeforeEach
    void setUp() {
        when(guard.currentUserId()).thenReturn(30L);
        when(users.getReferenceById(30L)).thenReturn(staff);
        when(messages.findThread(any())).thenReturn(List.of());
    }

    @Test
    void acknowledgingJustifiesUnjustifiedAbsencesInTheRange() {
        AbsenceReport report = AbsenceReport.builder().id(3L).student(child).school(school)
                .startDate(monday).endDate(monday.plusDays(1)).reason("Fièvre")
                .status(AbsenceReportStatus.PENDING).build();
        when(reports.findById(3L)).thenReturn(Optional.of(report));
        Attendance inRange = attendance(monday, AttendanceStatus.ABSENT, null);
        Attendance alreadyJustified = attendance(monday.plusDays(1), AttendanceStatus.LATE, "Bus");
        Attendance outOfRange = attendance(monday.plusDays(3), AttendanceStatus.ABSENT, null);
        Attendance present = attendance(monday.plusDays(1), AttendanceStatus.PRESENT, null);
        when(attendances.findByStudentIdOrderByAttendanceDateDesc(11L))
                .thenReturn(List.of(outOfRange, alreadyJustified, present, inRange));

        var item = service.acknowledge(3L, new FamilyContactDto.AbsenceDecision("Bon rétablissement"));

        assertEquals(AbsenceReportStatus.ACKNOWLEDGED, item.status());
        assertEquals(1, item.justifiedAttendances());
        assertEquals("Signalée par le parent : Fièvre", inRange.getJustification());
        assertEquals("Bus", alreadyJustified.getJustification());
        assertNull(outOfRange.getJustification());
        assertEquals("Ali Zongo", item.handledByName());
        verify(guard).requireSchoolModule(1L, StaffModule.STUDENTS);
    }

    @Test
    void rejectionRequiresAComment() {
        assertThrows(IllegalArgumentException.class,
                () -> service.reject(3L, new FamilyContactDto.AbsenceDecision(" ")));
    }

    @Test
    void alreadyHandledReportCannotBeDecidedAgain() {
        AbsenceReport report = AbsenceReport.builder().id(4L).student(child).school(school)
                .startDate(monday).endDate(monday).reason("x").status(AbsenceReportStatus.CANCELLED).build();
        when(reports.findById(4L)).thenReturn(Optional.of(report));
        assertThrows(IllegalArgumentException.class, () -> service.acknowledge(4L, null));
    }

    @Test
    void staffWithoutStudentsModuleCannotReadConversations() {
        SchoolConversation conversation = SchoolConversation.builder().id(8L).school(school)
                .parentUser(User.builder().id(7L).firstName("Awa").lastName("Kaboré").build()).subject("s").build();
        when(conversations.findById(8L)).thenReturn(Optional.of(conversation));
        doThrow(new AccessDeniedException("non")).when(guard).requireSchoolModule(eq(1L), any(StaffModule[].class));
        assertThrows(AccessDeniedException.class, () -> service.thread(8L));
        assertThrows(AccessDeniedException.class, () -> service.conversations(1L));
    }

    @Test
    void schoolReplyMarksTheConversationUnreadForTheParent() {
        SchoolConversation conversation = SchoolConversation.builder().id(8L).school(school)
                .parentUser(User.builder().id(7L).firstName("Awa").lastName("Kaboré").build()).subject("s")
                .unreadBySchool(true).unreadByParent(false).build();
        when(conversations.findById(8L)).thenReturn(Optional.of(conversation));
        when(participants.findByConversationIdAndSchoolId(8L, 1L))
                .thenReturn(Optional.of(ConversationParticipant.builder().conversation(conversation).school(school).build()));
        service.reply(8L, new FamilyContactDto.ReplyRequest("Bien reçu"));
        assertTrue(conversation.isUnreadByParent());
        assertFalse(conversation.isUnreadBySchool());
        verify(messages).save(argThat(m -> m.isFromSchool() && m.getSender() == staff));
    }

    private Attendance attendance(LocalDate date, AttendanceStatus status, String justification) {
        return Attendance.builder().student(child).attendanceDate(date).status(status)
                .justification(justification).build();
    }
}
