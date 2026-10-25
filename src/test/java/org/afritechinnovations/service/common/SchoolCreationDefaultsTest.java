package org.afritechinnovations.service.common;

import org.afritechinnovations.dto.common.SchoolDto;
import org.afritechinnovations.model.common.*;
import org.afritechinnovations.repository.common.*;
import org.afritechinnovations.security.AccessGuard;
import org.afritechinnovations.service.academic.SchoolAcademicDefaults;
import org.junit.jupiter.api.Test;
import java.util.Optional;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.any;

class SchoolCreationDefaultsTest {
    @Test void initializesTheNewSchoolOnceAndNeverResetsItsCustomizationsOnUpdate() {
        var schools = mock(SchoolRepository.class);
        var users = mock(UserRepository.class);
        var defaults = mock(SchoolAcademicDefaults.class);
        var service = new SchoolService(schools, users, mock(org.springframework.jdbc.core.JdbcTemplate.class), mock(AccessGuard.class), defaults);
        var owner = User.builder().id(7L).build();
        when(users.findById(7L)).thenReturn(Optional.of(owner));
        when(schools.save(any())).thenAnswer(call -> { School school = call.getArgument(0); school.setId(1L); return school; });
        var dto = SchoolDto.builder().ownerId(7L).name("École").type(SchoolType.PRIMAIRE).build();
        service.create(dto);
        var school = School.builder().id(1L).owner(owner).type(SchoolType.PRIMAIRE).build();
        when(schools.findById(1L)).thenReturn(Optional.of(school));
        dto.setType(SchoolType.MIXTE); service.update(1L, dto);
        verify(defaults, times(1)).initialize(any());
    }
}
