package org.afritechinnovations.controler.common;

import org.afritechinnovations.security.AccessGuard;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import java.util.List;

class SupportIncidentControllerTest {
    @Test void filtersIncidentsBySelectedSchoolBeforePagination() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        AccessGuard guard = mock(AccessGuard.class);
        when(guard.currentUserId()).thenReturn(7L);
        when(jdbc.queryForList(anyString(), eq(Long.class), eq(2L))).thenReturn(List.of(7L));
        new SupportIncidentController(jdbc, guard).list(1, "test", 2L);
        verify(jdbc).queryForList(contains("AND i.school_id=?"), eq("%test%"), eq("%test%"), eq(2L), eq(30L));
    }

    @Test void preservesThePlatformWideAdministratorQueueWithoutASchoolFilter() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        AccessGuard guard = mock(AccessGuard.class);
        when(guard.isSuperAdmin()).thenReturn(true);
        new SupportIncidentController(jdbc, guard).list(0, "", null);
        verify(jdbc).queryForList(argThat(sql -> !sql.contains("s.owner_id=") && !sql.contains("AND i.school_id=?")), eq("%%"), eq("%%"), eq(0L));
    }
}
