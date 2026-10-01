package org.afritechinnovations.service.people;

import org.afritechinnovations.model.common.Role;
import org.afritechinnovations.model.common.School;
import org.afritechinnovations.model.common.SchoolUser;
import org.afritechinnovations.model.common.User;
import org.afritechinnovations.model.people.Teacher;
import org.afritechinnovations.repository.common.SchoolUserRepository;
import org.afritechinnovations.repository.people.TeacherRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TeacherProfileServiceTest {

    @Mock TeacherRepository teachers;
    @Mock SchoolUserRepository schoolUsers;
    @InjectMocks TeacherProfileService service;

    private final School school = School.builder().id(1L).build();
    private final School otherSchool = School.builder().id(2L).build();
    private final Role teacherRole = Role.builder().name("TEACHER").build();
    private final Role parentRole = Role.builder().name("PARENT").build();

    @Test
    void createsMissingProfilesForTeacherAccountsOnly() {
        User existing = User.builder().id(2L).build();
        User approved = User.builder().id(10L).build();
        User parent = User.builder().id(4L).build();
        when(teachers.findBySchoolId(1L)).thenReturn(List.of(Teacher.builder().id(1L).user(existing).school(school).build()));
        when(schoolUsers.findAllWithUserAndRoleBySchoolId(1L)).thenReturn(List.of(
                SchoolUser.builder().user(existing).school(school).role(teacherRole).build(),
                SchoolUser.builder().user(approved).school(school).role(teacherRole).build(),
                SchoolUser.builder().user(parent).school(school).role(parentRole).build()));

        service.ensureSchoolProfiles(1L);

        ArgumentCaptor<Teacher> saved = ArgumentCaptor.forClass(Teacher.class);
        verify(teachers).save(saved.capture());
        assertSame(approved, saved.getValue().getUser());
        assertSame(school, saved.getValue().getSchool());
    }

    @Test
    void ensureProfileReusesTheProfileOfTheSameSchool() {
        User user = User.builder().id(10L).build();
        Teacher current = Teacher.builder().id(5L).user(user).school(school).build();
        when(teachers.findByUserId(10L)).thenReturn(List.of(
                Teacher.builder().id(4L).user(user).school(otherSchool).build(), current));

        assertSame(current, service.ensureProfile(user, school));
        verify(teachers, never()).save(any());
    }
}