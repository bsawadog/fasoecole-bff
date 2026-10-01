package org.afritechinnovations.service.academic;

import org.afritechinnovations.dto.academic.SchoolClassDto;
import org.afritechinnovations.model.academic.AcademicYear;
import org.afritechinnovations.model.academic.Level;
import org.afritechinnovations.model.academic.SchoolClass;
import org.afritechinnovations.model.common.School;
import org.afritechinnovations.model.common.User;
import org.afritechinnovations.repository.academic.AcademicYearRepository;
import org.afritechinnovations.repository.academic.LevelRepository;
import org.afritechinnovations.repository.academic.SchoolClassRepository;
import org.afritechinnovations.repository.common.SchoolRepository;
import org.afritechinnovations.security.SchoolPermissions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SchoolClassServiceTest {
    @Mock SchoolClassRepository classes;
    @Mock AcademicYearRepository years;
    @Mock LevelRepository levels;
    @Mock SchoolRepository schools;
    @Mock
    SchoolPermissions permissions;

    @InjectMocks SchoolClassService service;

    private final School school = School.builder().id(1L).owner(User.builder().id(10L).build()).build();

    private SchoolClassDto dto() {
        return SchoolClassDto.builder().schoolId(1L).academicYearId(2L).levelId(3L)
                .name("CM1 A").capacity(30).build();
    }

    @Test
    void createsClassWithMatchingSchoolYearAndLevel() {
        when(schools.findById(1L)).thenReturn(Optional.of(school));
        when(years.findById(2L)).thenReturn(Optional.of(AcademicYear.builder().id(2L).school(school).build()));
        when(levels.findById(3L)).thenReturn(Optional.of(Level.builder().id(3L).school(school).build()));
        when(classes.save(any(SchoolClass.class))).thenAnswer(invocation -> invocation.getArgument(0));

        var result = service.create(dto(), 10L, false);

        assertEquals(1L, result.getSchoolId());
        assertEquals(2L, result.getAcademicYearId());
        assertEquals(3L, result.getLevelId());
    }

    @Test
    void refusesYearFromAnotherSchool() {
        when(schools.findById(1L)).thenReturn(Optional.of(school));
        when(years.findById(2L)).thenReturn(Optional.of(AcademicYear.builder().id(2L)
                .school(School.builder().id(9L).build()).build()));

        assertThrows(IllegalArgumentException.class, () -> service.create(dto(), 10L, false));
        verifyNoInteractions(levels, classes);
    }

    @Test
    void refusesLevelFromAnotherSchoolOnUpdate() {
        when(classes.findById(4L)).thenReturn(Optional.of(SchoolClass.builder().id(4L).school(school).build()));
        when(years.findById(2L)).thenReturn(Optional.of(AcademicYear.builder().id(2L).school(school).build()));
        when(levels.findById(3L)).thenReturn(Optional.of(Level.builder().id(3L)
                .school(School.builder().id(9L).build()).build()));

        assertThrows(IllegalArgumentException.class, () -> service.update(4L, dto(), 10L, false));
        verify(classes, never()).save(any());
    }

    @Test
    void refusesChangingClassSchoolOnUpdate() {
        when(classes.findById(4L)).thenReturn(Optional.of(SchoolClass.builder().id(4L).school(school).build()));
        SchoolClassDto request = dto();
        request.setSchoolId(9L);

        assertThrows(IllegalArgumentException.class, () -> service.update(4L, request, 10L, false));
        verifyNoInteractions(years, levels);
    }

    @Test
    void refusesAnotherOwnerBeforeLoadingRelatedData() {
        when(schools.findById(1L)).thenReturn(Optional.of(school));

        assertThrows(AccessDeniedException.class, () -> service.create(dto(), 11L, false));
        verifyNoInteractions(years, levels, classes);
    }

    @Test
    void refusesAnotherOwnerDeletingClass() {
        SchoolClass schoolClass = SchoolClass.builder().id(4L).school(school).build();
        when(classes.findById(4L)).thenReturn(Optional.of(schoolClass));

        assertThrows(AccessDeniedException.class, () -> service.delete(4L, 11L, false));
        verify(classes, never()).delete(any());
    }
}
