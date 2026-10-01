package org.afritechinnovations.service.common;

import org.afritechinnovations.model.common.School;
import org.afritechinnovations.model.common.User;
import org.afritechinnovations.repository.common.SchoolRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.AccessDeniedException;

import java.util.Optional;
import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
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

    @Test
    void expectedAmountExcludesCancelledInvoicesWhileReceivedAmountCountsActualPayments() {
        School school = School.builder().id(5L).owner(User.builder().id(10L).build()).build();
        when(schoolRepository.findById(5L)).thenReturn(Optional.of(school));
        lenient().when(jdbcTemplate.queryForObject(anyString(), eq(BigDecimal.class), eq(5L)))
                .thenAnswer(invocation -> {
                    String sql = invocation.getArgument(0);
                    if (sql.contains("SUM(i.amount_due)")) return new BigDecimal("80.00");
                    if (sql.contains("SUM(p.amount)")) return new BigDecimal("20.00");
                    if (sql.contains("GREATEST(i.amount_due")) return new BigDecimal("60.00");
                    return BigDecimal.ZERO;
                });

        var dashboard = dashboardService.getDashboard(5L, 10L);

        assertEquals(new BigDecimal("80.00"), dashboard.expectedAmount());
        assertEquals(new BigDecimal("20.00"), dashboard.receivedAmount());
        assertEquals(new BigDecimal("60.00"), dashboard.outstandingAmount());
        ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
        verify(jdbcTemplate, atLeastOnce()).queryForObject(sql.capture(), eq(BigDecimal.class), eq(5L));
        assertTrue(sql.getAllValues().stream().anyMatch(query ->
                query.contains("SUM(i.amount_due)") && query.contains("i.status <> 'CANCELLED'")));
        assertTrue(sql.getAllValues().stream().anyMatch(query ->
                query.contains("SUM(p.amount)") && !query.contains("i.status <> 'CANCELLED'")));
        assertTrue(sql.getAllValues().stream().anyMatch(query ->
                query.contains("GREATEST(i.amount_due") && query.contains("i.status IN ('PENDING', 'OVERDUE')")));
    }
}
