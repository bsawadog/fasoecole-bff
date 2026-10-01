package org.afritechinnovations.security;

import org.afritechinnovations.model.common.Role;
import org.afritechinnovations.model.common.School;
import org.afritechinnovations.model.common.SchoolUser;
import org.afritechinnovations.model.common.User;
import org.afritechinnovations.model.people.Parent;
import org.afritechinnovations.model.people.ParentStudent;
import org.afritechinnovations.model.people.Student;
import org.afritechinnovations.repository.academic.AcademicYearRepository;
import org.afritechinnovations.repository.academic.SchoolClassRepository;
import org.afritechinnovations.repository.common.SchoolRepository;
import org.afritechinnovations.repository.common.SchoolUserRepository;
import org.afritechinnovations.repository.people.ParentRepository;
import org.afritechinnovations.repository.people.ParentStudentRepository;
import org.afritechinnovations.repository.people.StudentRepository;
import org.afritechinnovations.repository.people.TeacherRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AccessGuardTest {
    @Mock SchoolRepository schools;
    @Mock SchoolUserRepository schoolUsers;
    @Mock SchoolClassRepository classes;
    @Mock AcademicYearRepository years;
    @Mock StudentRepository students;
    @Mock TeacherRepository teachers;
    @Mock ParentRepository parents;
    @Mock ParentStudentRepository parentStudents;
    @InjectMocks AccessGuard guard;

    private final School school1 = School.builder().id(1L).owner(User.builder().id(10L).build()).build();
    private final School school2 = School.builder().id(2L).owner(User.builder().id(10L).build()).build();

    @AfterEach
    void clear() {
        SecurityContextHolder.clearContext();
    }

    private void login(long userId, String... roles) {
        User user = User.builder().id(userId).active(true).approved(true).build();
        UserPrincipal principal = new UserPrincipal(user, List.of(roles));
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities()));
    }

    @Test
    void onlyTheOwnerManagesTheSchool() {
        when(schools.findById(1L)).thenReturn(Optional.of(school1));
        login(10L, "SCHOOL_ADMIN");
        assertDoesNotThrow(() -> guard.requireOwnedSchool(1L));

        login(11L, "SCHOOL_ADMIN");
        assertThrows(AccessDeniedException.class, () -> guard.requireOwnedSchool(1L));
    }

    @Test
    void teacherIsStaffOnlyOfHisOwnSchool() {
        when(schools.findById(1L)).thenReturn(Optional.of(school1));
        when(schools.findById(2L)).thenReturn(Optional.of(school2));
        when(schoolUsers.findByUserId(13L)).thenReturn(List.of(SchoolUser.builder()
                .school(school2).role(Role.builder().name("TEACHER").build()).build()));
        login(13L, "TEACHER");

        assertDoesNotThrow(() -> guard.requireSchoolStaff(2L));
        assertThrows(AccessDeniedException.class, () -> guard.requireSchoolStaff(1L));
    }

    @Test
    void parentReadsOnlyHisOwnChildren() {
        Student child = Student.builder().id(20L).school(school1).user(User.builder().id(30L).build()).build();
        Student other = Student.builder().id(21L).school(school1).user(User.builder().id(31L).build()).build();
        Parent parent = Parent.builder().id(5L).build();
        when(students.findById(20L)).thenReturn(Optional.of(child));
        when(students.findById(21L)).thenReturn(Optional.of(other));
        when(parents.findByUserId(11L)).thenReturn(Optional.of(parent));
        when(parentStudents.findByParentId(5L))
                .thenReturn(List.of(ParentStudent.builder().parent(parent).student(child).build()));
        login(11L, "PARENT");

        assertDoesNotThrow(() -> guard.requireStudentReader(20L));
        assertThrows(AccessDeniedException.class, () -> guard.requireStudentReader(21L));
    }

    @Test
    void messagingRequiresACommonSchool() {
        lenient().when(schoolUsers.findByUserId(13L)).thenReturn(List.of(SchoolUser.builder().school(school2).build()));
        lenient().when(schools.findByOwnerId(10L)).thenReturn(List.of(school1, school2));
        login(13L, "TEACHER");

        assertTrue(guard.sharesSchoolWith(10L));
        assertFalse(guard.sharesSchoolWith(99L));
    }
}
