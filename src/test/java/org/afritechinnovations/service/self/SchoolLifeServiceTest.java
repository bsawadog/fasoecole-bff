package org.afritechinnovations.service.self;

import org.afritechinnovations.security.AccessGuard;
import org.afritechinnovations.model.common.StaffModule;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.AccessDeniedException;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class SchoolLifeServiceTest {
    final JdbcTemplate jdbc=mock(JdbcTemplate.class);
    final AccessGuard guard=mock(AccessGuard.class);
    final FamilySpaceService family=mock(FamilySpaceService.class);
    final TeacherSpaceService teacher=mock(TeacherSpaceService.class);
    final SchoolLifeService service=new SchoolLifeService(jdbc,guard,family,teacher);

    @Test void schoolOverviewRequiresDelegatedModuleBeforeReadingData() {
        doThrow(new AccessDeniedException("Forbidden")).when(guard).requireSchoolModule(1L,StaffModule.CALENDAR);
        assertThrows(AccessDeniedException.class,()->service.ownerOverview(1L));
        verifyNoInteractions(jdbc);
    }
    @Test void classOverviewRequiresTeacherAssignmentBeforeReadingData() {
        when(guard.currentUserId()).thenReturn(7L);
        when(teacher.requireTaughtClass(7L,3L)).thenThrow(new AccessDeniedException("Forbidden"));
        assertThrows(AccessDeniedException.class,()->service.teacherOverview(3L));
        verifyNoInteractions(jdbc);
    }
    @Test void familyOverviewRequiresChildRelationshipBeforeReadingData() {
        when(guard.currentUserId()).thenReturn(7L);
        when(family.requireGuardedChild(7L,9L)).thenThrow(new AccessDeniedException("Forbidden"));
        assertThrows(AccessDeniedException.class,()->service.studentOverview(9L));
        verifyNoInteractions(jdbc);
    }
    @Test void suspendedSchoolCannotUseNewModules() {
        doThrow(new AccessDeniedException("Suspended")).when(guard).requireApprovedSchool(1L);
        assertThrows(AccessDeniedException.class,()->service.ownerOverview(1L));
        verifyNoInteractions(jdbc);
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.EnumSource(org.afritechinnovations.dto.self.SchoolLifeDto.LifeModule.class)
    void eachOverviewChecksItsOwnPermission(org.afritechinnovations.dto.self.SchoolLifeDto.LifeModule module) {
        var permission = StaffModule.valueOf(module.name());
        doThrow(new AccessDeniedException("Forbidden")).when(guard).requireSchoolModule(1L, permission);
        assertThrows(AccessDeniedException.class, () -> service.ownerOverview(1L, module));
        verifyNoInteractions(jdbc);
    }
}
