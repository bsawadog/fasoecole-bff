package org.afritechinnovations.service.communication;

import org.afritechinnovations.dto.communication.FamilyContactDto;
import org.afritechinnovations.dto.self.SelfServiceDto;
import org.afritechinnovations.model.common.School;
import org.afritechinnovations.model.common.User;
import org.afritechinnovations.model.communication.AbsenceReport;
import org.afritechinnovations.model.communication.AbsenceReportStatus;
import org.afritechinnovations.model.communication.SchoolConversation;
import org.afritechinnovations.model.people.Student;
import org.afritechinnovations.repository.common.SchoolRepository;
import org.afritechinnovations.repository.common.UserRepository;
import org.afritechinnovations.repository.communication.AbsenceReportRepository;
import org.afritechinnovations.repository.communication.SchoolConversationMessageRepository;
import org.afritechinnovations.repository.communication.SchoolConversationRepository;
import org.afritechinnovations.service.self.FamilySpaceService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
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
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ParentContactServiceTest {
    @Mock FamilySpaceService familySpace;
    @Mock AbsenceReportRepository reports;
    @Mock SchoolConversationRepository conversations;
    @Mock SchoolConversationMessageRepository messages;
    @Mock SchoolRepository schools;
    @Mock UserRepository users;
    @InjectMocks ParentContactService service;

    private final School schoolA = School.builder().id(1L).name("École A").build();
    private final User parent = User.builder().id(7L).firstName("Awa").lastName("Kaboré").build();
    private final Student child = Student.builder().id(11L).school(schoolA).registrationNumber("MAT-11")
            .user(User.builder().id(111L).firstName("Moussa").lastName("Kaboré").build()).build();

    @BeforeEach
    void setUp() {
        when(familySpace.requireGuardedChild(7L, 11L)).thenReturn(child);
        when(familySpace.requireGuardedChild(7L, 99L))
                .thenThrow(new AccessDeniedException("Vous n'avez pas accès au dossier de cet élève"));
        when(familySpace.parentSchools(7L)).thenReturn(List.of(
                new SelfServiceDto.SchoolContact(1L, "École A", null, null, null)));
        when(users.getReferenceById(7L)).thenReturn(parent);
        when(users.findById(7L)).thenReturn(Optional.of(parent));
        when(schools.findById(1L)).thenReturn(Optional.of(schoolA));
        when(reports.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(conversations.save(any())).thenAnswer(inv -> {
            SchoolConversation c = inv.getArgument(0);
            c.setId(5L);
            return c;
        });
        when(messages.findThread(any())).thenReturn(List.of());
    }

    @Test
    void parentReportsAnAbsenceForHisChild() {
        LocalDate today = LocalDate.now();
        var item = service.reportAbsence(7L, 11L,
                new FamilyContactDto.AbsenceReportRequest(today, today.plusDays(2), "  Fièvre  "));
        ArgumentCaptor<AbsenceReport> saved = ArgumentCaptor.forClass(AbsenceReport.class);
        verify(reports).save(saved.capture());
        assertEquals(AbsenceReportStatus.PENDING, saved.getValue().getStatus());
        assertEquals(1L, saved.getValue().getSchool().getId());
        assertEquals("Fièvre", item.reason());
        assertEquals("Awa Kaboré", item.reportedByName());
    }

    @Test
    void reportDatesAreValidated() {
        LocalDate today = LocalDate.now();
        assertThrows(IllegalArgumentException.class, () -> service.reportAbsence(7L, 11L,
                new FamilyContactDto.AbsenceReportRequest(today, today.minusDays(1), "x")));
        assertThrows(IllegalArgumentException.class, () -> service.reportAbsence(7L, 11L,
                new FamilyContactDto.AbsenceReportRequest(today.minusDays(40), today.minusDays(39), "x")));
        assertThrows(IllegalArgumentException.class, () -> service.reportAbsence(7L, 11L,
                new FamilyContactDto.AbsenceReportRequest(today, today.plusDays(90), "x")));
        verify(reports, never()).save(any());
    }

    @Test
    void parentCannotReportForAChildHeDoesNotGuard() {
        LocalDate today = LocalDate.now();
        assertThrows(AccessDeniedException.class, () -> service.reportAbsence(7L, 99L,
                new FamilyContactDto.AbsenceReportRequest(today, today, "x")));
    }

    @Test
    void onlyPendingReportsCanBeCancelled() {
        AbsenceReport handled = AbsenceReport.builder().id(3L).student(child).school(schoolA)
                .status(AbsenceReportStatus.ACKNOWLEDGED).build();
        when(reports.findById(3L)).thenReturn(Optional.of(handled));
        assertThrows(IllegalArgumentException.class, () -> service.cancelReport(7L, 3L));

        AbsenceReport pending = AbsenceReport.builder().id(4L).student(child).school(schoolA)
                .status(AbsenceReportStatus.PENDING).build();
        when(reports.findById(4L)).thenReturn(Optional.of(pending));
        assertEquals(AbsenceReportStatus.CANCELLED, service.cancelReport(7L, 4L).status());
    }

    @Test
    void parentStartsAConversationWithASchoolWhereHeHasAccess() {
        var thread = service.start(7L, new FamilyContactDto.NewConversationRequest(1L, 11L, "Rendez-vous",
                "Bonjour, je souhaite vous rencontrer."));
        assertEquals("Rendez-vous", thread.conversation().subject());
        assertEquals("Moussa Kaboré", thread.conversation().studentName());
        verify(messages).save(argThat(m -> !m.isFromSchool() && m.getContent().startsWith("Bonjour")));
    }

    @Test
    void parentCannotWriteToASchoolWithoutActiveAccess() {
        assertThrows(AccessDeniedException.class, () -> service.start(7L,
                new FamilyContactDto.NewConversationRequest(2L, null, "Objet", "Message")));
        verify(conversations, never()).save(any());
    }

    @Test
    void parentCannotReadSomeoneElsesConversation() {
        SchoolConversation other = SchoolConversation.builder().id(8L).school(schoolA)
                .parentUser(User.builder().id(70L).firstName("X").lastName("Y").build()).subject("s").build();
        when(conversations.findById(8L)).thenReturn(Optional.of(other));
        assertThrows(AccessDeniedException.class, () -> service.thread(7L, 8L));
        assertThrows(AccessDeniedException.class,
                () -> service.reply(7L, 8L, new FamilyContactDto.ReplyRequest("hello")));
    }
}