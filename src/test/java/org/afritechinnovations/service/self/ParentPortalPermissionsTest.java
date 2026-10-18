package org.afritechinnovations.service.self;

import org.afritechinnovations.model.common.StaffModule;
import org.afritechinnovations.security.AccessGuard;
import org.afritechinnovations.service.communication.ConversationMessagingService;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.AccessDeniedException;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ParentPortalPermissionsTest {
    final JdbcTemplate jdbc = mock(JdbcTemplate.class);
    final AccessGuard guard = mock(AccessGuard.class);
    final FamilySpaceService family = mock(FamilySpaceService.class);
    final ParentPortalService service = new ParentPortalService(jdbc, guard, family,
            mock(ConversationMessagingService.class), mock(TeacherSpaceService.class));

    @Test void publicationsAndFilesRejectUnauthorizedStudentBeforeQuerying() {
        when(guard.currentUserId()).thenReturn(7L);
        when(family.requireReadableStudent(7L, 99L)).thenThrow(new AccessDeniedException("Forbidden"));
        assertThrows(AccessDeniedException.class, () -> service.studentPosts(99L, org.afritechinnovations.dto.self.ParentPortalDto.Kind.HOMEWORK));
        assertThrows(AccessDeniedException.class, () -> service.studentPosts(99L, org.afritechinnovations.dto.self.ParentPortalDto.Kind.DOCUMENT));
        assertThrows(AccessDeniedException.class, () -> service.studentDownload(99L, 1L, 2L));
        verifyNoInteractions(jdbc);
        verify(family, never()).requireGuardedChild(anyLong(), anyLong());
    }

    @Test void studentTargetsRequirePortalPermissionBeforeReadingAnything() {
        doThrow(new AccessDeniedException("Forbidden")).when(guard).requireSchoolModule(1L, StaffModule.PARENT_PORTAL);
        assertThrows(AccessDeniedException.class, () -> service.schoolStudents(1L, 2L));
        verifyNoInteractions(jdbc);
    }

    @Test void studentTargetsCannotReadAClassFromAnotherSchool() {
        when(guard.schoolOfClass(2L)).thenReturn(3L);
        doThrow(new IllegalArgumentException("Different school")).when(guard).requireSame(1L, 3L, "classe");
        assertThrows(IllegalArgumentException.class, () -> service.schoolStudents(1L, 2L));
        verifyNoInteractions(jdbc);
    }
}
