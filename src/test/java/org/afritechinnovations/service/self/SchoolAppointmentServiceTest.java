package org.afritechinnovations.service.self;

import org.afritechinnovations.dto.self.SchoolAppointmentDto.Request;
import org.afritechinnovations.dto.self.ParentPortalDto.AppointmentDecision;
import org.afritechinnovations.dto.self.ParentPortalDto.Decision;
import org.afritechinnovations.dto.communication.FamilyContactDto.Recipient;
import org.afritechinnovations.model.common.StaffModule;
import org.afritechinnovations.security.AccessGuard;
import org.afritechinnovations.service.communication.ConversationMessagingService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.AccessDeniedException;
import java.time.LocalDateTime;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class SchoolAppointmentServiceTest {
    private final JdbcTemplate jdbc = mock(JdbcTemplate.class);
    private final AccessGuard guard = mock(AccessGuard.class);
    private final ConversationMessagingService messaging = mock(ConversationMessagingService.class);
    private final SchoolAppointmentService service = new SchoolAppointmentService(jdbc, guard, messaging);
    @BeforeEach void setup() { when(guard.currentUserId()).thenReturn(10L); }
    @Test void onlyParentsAndTeachersCanBeInvited() {
        when(messaging.recipients(10L, 2L, null)).thenReturn(List.of(
            new Recipient(7L, "Parent", "PARENT", null), new Recipient(8L, "Teacher", "ENSEIGNANT", null),
            new Recipient(9L, "Student", "ELEVE", null)));
        assertEquals(List.of(7L, 8L), service.recipients(2L).stream().map(Recipient::userId).toList());
        verify(guard).requireSchoolModule(2L, StaffModule.STUDENTS);
    }
    @Test void rejectsAnInaccessibleRecipientBeforeWriting() {
        when(messaging.recipients(10L, 2L, null)).thenReturn(List.of());
        assertThrows(AccessDeniedException.class, () -> service.create(2L, new Request(999L, LocalDateTime.now().plusDays(1), "Meeting")));
        verifyNoInteractions(jdbc);
    }
    @Test void rejectsPastDatesBeforeWriting() {
        when(messaging.recipients(10L, 2L, null)).thenReturn(List.of(new Recipient(7L, "Parent", "PARENT", null)));
        assertThrows(IllegalArgumentException.class, () -> service.create(2L, new Request(7L, LocalDateTime.now().minusDays(1), "Meeting")));
        verifyNoInteractions(jdbc);
    }
    @Test void decisionsAreRestrictedToTheRecipientAndPendingStatus() {
        assertThrows(IllegalArgumentException.class, () -> service.decide(8L, new AppointmentDecision(Decision.REJECTED, "Unavailable")));
        verify(jdbc).update(contains("recipient_user_id=? AND status='PENDING'"), eq("REJECTED"), eq("Unavailable"), eq(8L), eq(10L), eq("REJECTED"), any(LocalDateTime.class));
    }
    @Test void cancellationsAreRestrictedToTheirSchoolAndOrganizer() {
        assertThrows(IllegalArgumentException.class, () -> service.cancel(2L, 8L));
        verify(guard).requireSchoolModule(2L, StaffModule.STUDENTS);
        verify(jdbc).update(contains("school_id=? AND organizer_user_id=?"), eq(8L), eq(2L), eq(10L));
    }
}
