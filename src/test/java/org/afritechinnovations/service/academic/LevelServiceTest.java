package org.afritechinnovations.service.academic;

import org.afritechinnovations.dto.academic.LevelDto;
import org.afritechinnovations.model.academic.Level;
import org.afritechinnovations.model.common.School;
import org.afritechinnovations.model.common.User;
import org.afritechinnovations.repository.academic.LevelRepository;
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
class LevelServiceTest {
    @Mock LevelRepository levels;
    @Mock SchoolRepository schools;
    @Mock
    SchoolPermissions permissions;

    @InjectMocks LevelService service;

    @Test
    void rejectsAnotherSchoolsLevelOnUpdate() {
        School school = School.builder().id(1L).owner(User.builder().id(10L).build()).build();
        when(levels.findById(3L)).thenReturn(Optional.of(Level.builder().id(3L).school(school).build()));
        LevelDto request = LevelDto.builder().schoolId(2L).name("CM1").cycle("PRIMAIRE").orderIndex(5).build();

        assertThrows(IllegalArgumentException.class, () -> service.update(3L, request, 10L, false));
        verify(levels, never()).save(any());
    }

    @Test
    void rejectsLevelCreationByAnotherSchoolOwner() {
        when(schools.findById(1L)).thenReturn(Optional.of(School.builder().id(1L)
                .owner(User.builder().id(10L).build()).build()));
        LevelDto request = LevelDto.builder().schoolId(1L).name("CM1").cycle("PRIMAIRE").orderIndex(5).build();

        assertThrows(AccessDeniedException.class, () -> service.create(request, 11L, false));
        verifyNoInteractions(levels);
    }

    @Test
    void rejectsLevelDeletionByAnotherSchoolOwner() {
        Level level = Level.builder().id(3L).school(School.builder().id(1L)
                .owner(User.builder().id(10L).build()).build()).build();
        when(levels.findById(3L)).thenReturn(Optional.of(level));

        assertThrows(AccessDeniedException.class, () -> service.delete(3L, 11L, false));
        verify(levels, never()).delete(any());
    }
}
