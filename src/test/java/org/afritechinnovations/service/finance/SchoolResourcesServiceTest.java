package org.afritechinnovations.service.finance;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.afritechinnovations.model.common.*;
import org.afritechinnovations.repository.common.SchoolRepository;
import org.afritechinnovations.security.SchoolPermissions;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.AccessDeniedException;
import java.math.BigDecimal;
import java.util.Optional;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class SchoolResourcesServiceTest {
    @Test void requiresEachDelegatedPermissionAndRejectsForeignOwnersBeforeReadingAnyData() {
        var jdbc = mock(JdbcTemplate.class);
        var schools = mock(SchoolRepository.class);
        var permissions = mock(SchoolPermissions.class);
        when(schools.findById(1L)).thenReturn(Optional.of(School.builder().id(1L).status(SchoolStatus.ACTIVE).owner(User.builder().id(10L).build()).build()));
        when(permissions.staffAllows(1L, 20L, StaffModule.INVENTORY)).thenReturn(true);
        var service = new SchoolResourcesService(jdbc, schools, permissions, new ObjectMapper());
        assertThrows(AccessDeniedException.class, () -> service.assets(1L,20L,false));
        assertThrows(AccessDeniedException.class, () -> service.stock(1L,30L,false));
        verifyNoInteractions(jdbc);
        service.stock(1L,20L,false);
        verify(permissions).staffAllows(1L,20L,StaffModule.INVENTORY);
    }

    @Test void refusesNegativeNonFinitePrecisionAndOversizedQuantities() {
        assertEquals(new BigDecimal("0.000"), SchoolResourcesService.quantity(BigDecimal.ZERO));
        assertEquals(new BigDecimal("1.125"), SchoolResourcesService.quantity(new BigDecimal("1.125")));
        assertThrows(IllegalArgumentException.class, () -> SchoolResourcesService.quantity(new BigDecimal("-0.001")));
        assertThrows(IllegalArgumentException.class, () -> SchoolResourcesService.quantity(new BigDecimal("1.0001")));
        assertThrows(IllegalArgumentException.class, () -> SchoolResourcesService.quantity(new BigDecimal("100000000000")));
    }
}
