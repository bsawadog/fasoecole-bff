package org.afritechinnovations.service.common;

import org.afritechinnovations.dto.academic.OwnerGradeDto;
import org.afritechinnovations.model.academic.GradePeriodStatus;
import org.afritechinnovations.model.common.School;
import org.afritechinnovations.model.common.User;
import org.afritechinnovations.repository.common.SchoolRepository;
import org.afritechinnovations.service.academic.OwnerGradeService;
import org.afritechinnovations.security.SchoolPermissions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.AccessDeniedException;

import java.util.List;
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

    @Mock
    private OwnerGradeService ownerGradeService;

    @Mock
    SchoolPermissions permissions;

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
        when(jdbcTemplate.query(anyString(), org.mockito.ArgumentMatchers.<org.springframework.jdbc.core.RowMapper<Long>>any(), eq(5L)))
                .thenReturn(List.of(9L));
        lenient().when(jdbcTemplate.queryForObject(anyString(), eq(BigDecimal.class), eq(5L), eq(9L)))
                .thenAnswer(invocation -> {
                    String sql = invocation.getArgument(0);
                    if (sql.contains("SUM(GREATEST(i.amount_due - COALESCE(i.discount_amount,0),0))")) return new BigDecimal("80.00");
                    if (sql.contains("SUM(p.amount)")) return new BigDecimal("20.00");
                    if (sql.contains("GREATEST(i.amount_due")) return new BigDecimal("60.00");
                    return BigDecimal.ZERO;
                });
        lenient().when(jdbcTemplate.queryForObject(anyString(), eq(BigDecimal.class), eq(5L), eq(9L), eq(9L)))
                .thenReturn(new BigDecimal("20.00"));

        var dashboard = dashboardService.getDashboard(5L, 10L);

        assertEquals(new BigDecimal("80.00"), dashboard.expectedAmount());
        assertEquals(new BigDecimal("20.00"), dashboard.receivedAmount());
        assertEquals(new BigDecimal("60.00"), dashboard.outstandingAmount());
        ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
        verify(jdbcTemplate, atLeastOnce()).queryForObject(sql.capture(), eq(BigDecimal.class), eq(5L), eq(9L));
        verify(jdbcTemplate).queryForObject(sql.capture(), eq(BigDecimal.class), eq(5L), eq(9L), eq(9L));
        assertTrue(sql.getAllValues().stream().anyMatch(query ->
                query.contains("SUM(GREATEST(i.amount_due - COALESCE(i.discount_amount,0),0))") && query.contains("i.status <> 'CANCELLED'")));
        assertTrue(sql.getAllValues().stream().anyMatch(query ->
                query.contains("SUM(p.amount)") && !query.contains("i.status <> 'CANCELLED'")));
        assertTrue(sql.getAllValues().stream().anyMatch(query ->
                query.contains("COALESCE(paid.amount, 0)") && query.contains("i.status <> 'CANCELLED'")));
    }

    @Test
    void schoolAverageComesFromTheSameComputationAsGradesPage() {
        School school = School.builder().id(5L).owner(User.builder().id(10L).build()).build();
        when(schoolRepository.findById(5L)).thenReturn(Optional.of(school));
        OwnerGradeDto.PeriodInfo info = new OwnerGradeDto.PeriodInfo(3L, 5L, 1L, "2025-2026", "TERM1",
                "1er trimestre", null, null, BigDecimal.TEN, GradePeriodStatus.OPEN, null, 2);
        when(ownerGradeService.dashboardSummary(eq(5L), eq(10L), any())).thenReturn(Optional.of(new OwnerGradeDto.SchoolSummary(
                info, List.of(), new BigDecimal("11.43"), new BigDecimal("60.0"), 6, 5)));

        var dashboard = dashboardService.getDashboard(5L, 10L);

        assertEquals(new BigDecimal("11.43"), dashboard.schoolAverage());
        assertEquals("1er trimestre", dashboard.averagePeriodName());
        assertEquals(5, dashboard.rankedStudents());
    }
}
