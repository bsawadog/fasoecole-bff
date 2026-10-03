package org.afritechinnovations.service.common;

import org.afritechinnovations.model.common.*;
import org.afritechinnovations.model.people.*;
import org.afritechinnovations.repository.people.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.any;

@ExtendWith(MockitoExtension.class)
class SchoolIdentityAdmissionServiceTest {
    @Mock StudentRepository students;
    @Mock TeacherRepository teachers;
    @Mock org.afritechinnovations.repository.common.SchoolUserRepository memberships;
    @InjectMocks SchoolIdentityAdmissionService service;
    School school = School.builder().id(2L).build();
    User applicant = User.builder().id(7L).email("awa@test.bf").passwordSet(true).emailVerified(true).build();
    User placeholder = User.builder().id(8L).firstName("Awa").lastName("Diallo").passwordSet(false).build();
    Student student = Student.builder().id(11L).school(school).user(placeholder).registrationNumber("001").build();
    Teacher teacher = Teacher.builder().id(12L).school(school).user(placeholder).employeeNumber("001").build();

    void existing(RoleName role) {
        if (role == RoleName.STUDENT) when(students.findBySchoolIdAndRegistrationNumberForUpdate(2L, "001")).thenReturn(Optional.of(student));
        else when(teachers.findBySchoolIdAndEmployeeNumberForUpdate(2L, "001")).thenReturn(Optional.of(teacher));
    }

    @ParameterizedTest @EnumSource(value = RoleName.class, names = {"STUDENT", "TEACHER"})
    void schoolIdentifiersAreMandatoryTextAndRetainLeadingZeroes(RoleName role) {
        assertEquals("001", SchoolIdentityAdmissionService.normalize(role, " 001 "));
        assertThrows(IllegalArgumentException.class, () -> SchoolIdentityAdmissionService.normalize(role, null));
        assertThrows(IllegalArgumentException.class, () -> SchoolIdentityAdmissionService.normalize(role, " "));
        assertThrows(IllegalArgumentException.class, () -> SchoolIdentityAdmissionService.normalize(role, "x".repeat(51)));
        assertThrows(IllegalArgumentException.class, () -> SchoolIdentityAdmissionService.normalize(role, "001\n"));
    }

    @ParameterizedTest @EnumSource(value = RoleName.class, names = {"STUDENT", "TEACHER"})
    void unknownOrOtherSchoolNumberNeverCreatesADossier(RoleName role) {
        assertThrows(IllegalArgumentException.class, () -> service.attachApproved(applicant, school, role, "001"));
        verify(students, never()).save(any()); verify(teachers, never()).save(any());
    }

    @ParameterizedTest @EnumSource(value = RoleName.class, names = {"STUDENT", "TEACHER"})
    void approvalReusesAnUnclaimedDossierRatherThanCreatingAnother(RoleName role) {
        existing(role);
        service.attachApproved(applicant, school, role, "001");
        if (role == RoleName.STUDENT) { assertSame(applicant, student.getUser()); verify(students).save(student); }
        else { assertSame(applicant, teacher.getUser()); verify(teachers).save(teacher); }
        assertEquals(11L, student.getId()); assertEquals(12L, teacher.getId());
    }

    @ParameterizedTest @EnumSource(value = RoleName.class, names = {"STUDENT", "TEACHER"})
    void chosenPasswordOnAnotherAccountPreventsTakeover(RoleName role) {
        existing(role); placeholder.setPasswordSet(true);
        assertThrows(IllegalArgumentException.class, () -> service.attachApproved(applicant, school, role, "001"));
        assertSame(placeholder, student.getUser()); assertSame(placeholder, teacher.getUser());
        verify(students, never()).save(any()); verify(teachers, never()).save(any());
    }

    @ParameterizedTest @EnumSource(value = RoleName.class, names = {"STUDENT", "TEACHER"})
    void schoolInvitationAtAnotherEmailCannotBeTakenOver(RoleName role) {
        existing(role); placeholder.setEmail("other@test.bf");
        assertThrows(IllegalArgumentException.class, () -> service.attachApproved(applicant, school, role, "001"));
        verify(students, never()).save(any()); verify(teachers, never()).save(any());
    }

    @ParameterizedTest @EnumSource(value = RoleName.class, names = {"STUDENT", "TEACHER"})
    void sameAccountCanRequestAnotherSchoolWithoutDuplicatingItsDossier(RoleName role) {
        existing(role); student.setUser(applicant); teacher.setUser(applicant);
        assertDoesNotThrow(() -> service.attachApproved(applicant, school, role, "001"));
        verify(students, never()).save(any()); verify(teachers, never()).save(any());
    }

    @Test void secondStudentDossierInTheSameSchoolIsRejected() {
        existing(RoleName.STUDENT);
        when(students.findAllByUserId(7L)).thenReturn(List.of(Student.builder().id(99L).school(school).user(applicant).build()));
        assertThrows(IllegalArgumentException.class, () -> service.attachApproved(applicant, school, RoleName.STUDENT, "001"));
        verify(students, never()).save(any());
    }

    @Test void privateReviewUsesOnlyTheSelectedSchoolWithoutChangingDossiers() {
        when(teachers.findBySchoolIdAndEmployeeNumber(2L, "001")).thenReturn(Optional.of(teacher));
        assertEquals("Awa Diallo", service.review(2L, RoleName.TEACHER, "001"));
        assertTrue(service.review(3L, RoleName.TEACHER, "001").contains("introuvable"));
        verify(teachers, never()).save(any());
    }
}
