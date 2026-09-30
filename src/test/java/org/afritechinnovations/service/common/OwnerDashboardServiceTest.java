package org.afritechinnovations.service.common;

import org.afritechinnovations.model.common.School;
import org.afritechinnovations.model.common.User;
import org.afritechinnovations.repository.common.SchoolRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.AccessDeniedException;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OwnerDashboardServiceTest {

    @Mock
    private JdbcTemplate jdbcTemplate;

    @Mock
    private SchoolRepository schoolRepository;

    @InjectMocks
    private OwnerDashboardService dashboardService;

    @Test
    void refusesDashboardForSchoolOwnedByAnotherUser() {
        User owner = User.builder().id(10L).build();
        School school = School.builder().id(5L).owner(owner).build();
        when(schoolRepository.findById(5L)).thenReturn(Optional.of(school));

        assertThrows(AccessDeniedException.class, () -> dashboardService.getDashboard(5L, 20L));
        verifyNoInteractions(jdbcTemplate);
    }
}
