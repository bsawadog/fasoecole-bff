package org.afritechinnovations.service.common;

import org.afritechinnovations.model.common.*;
import org.afritechinnovations.model.people.*;
import org.afritechinnovations.repository.common.SchoolUserRepository;
import org.afritechinnovations.repository.people.*;
import org.afritechinnovations.repository.academic.ClassSubjectTeacherRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import java.util.List;
import java.util.Optional;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AccountOnboardingServiceTest {
    @Mock SchoolUserRepository memberships;
    @Mock ParentRepository parents;
    @Mock ParentStudentRepository children;
    @Mock StudentRepository students;
    @Mock StudentEnrollmentRepository enrollments;
    @Mock TeacherRepository teachers;
    @Mock ClassSubjectTeacherRepository assignments;
    @InjectMocks AccountOnboardingService service;
    final User user = User.builder().id(7L).emailVerified(true).build();
    void role(String role) { when(memberships.findByUserId(7L)).thenReturn(List.of(SchoolUser.builder().role(Role.builder().name(role).build()).build())); }

    @Test void invitationShowsAllRemainingSecuritySteps() {
        user.setEmailVerified(false); user.setPasswordSet(false); user.setApproved(false);
        assertEquals(List.of("PASSWORD_REQUIRED", "EMAIL_VERIFICATION_REQUIRED", "APPROVAL_REQUIRED"), service.steps(user));
    }
    @Test void parentMustHaveALinkedChildThenBecomesReady() {
        role("PARENT"); Parent parent = Parent.builder().id(40L).build();
        when(parents.findByUserId(7L)).thenReturn(Optional.of(parent));
        assertEquals(List.of("CHILD_LINK_REQUIRED"), service.steps(user));
        when(children.findByParentId(40L)).thenReturn(List.of(ParentStudent.builder().build()));
        assertEquals(List.of("READY"), service.steps(user));
    }
    @Test void studentMustHaveAnActiveEnrollment() {
        role("STUDENT");
        when(students.findAllByUserId(7L)).thenReturn(List.of(Student.builder().id(40L).build()));
        when(enrollments.findByStudentId(40L)).thenReturn(List.of(StudentEnrollment.builder().status(EnrollmentStatus.COMPLETED).build()));
        assertEquals(List.of("CLASS_ASSIGNMENT_REQUIRED"), service.steps(user));
        when(enrollments.findByStudentId(40L)).thenReturn(List.of(StudentEnrollment.builder().status(EnrollmentStatus.ACTIVE).build()));
        assertEquals(List.of("READY"), service.steps(user));
    }
    @Test void teacherMustHaveAnActiveClassAndSubjectAssignment() {
        role("TEACHER");
        when(teachers.findByUserId(7L)).thenReturn(List.of(Teacher.builder().id(40L).build()));
        assertEquals(List.of("TEACHING_ASSIGNMENT_REQUIRED"), service.steps(user));
        when(assignments.findByTeacherId(40L)).thenReturn(List.of(org.afritechinnovations.model.academic.ClassSubjectTeacher.builder().active(true).build()));
        assertEquals(List.of("READY"), service.steps(user));
    }
    @Test void staffAndOwnerHaveNoClassOrChildRequirements() {
        role("STAFF"); assertEquals(List.of("READY"), service.steps(user));
        role("SCHOOL_ADMIN"); assertEquals(List.of("READY"), service.steps(user));
        user.setActive(false); assertEquals(List.of("ACCOUNT_INACTIVE"), service.steps(user));
        verifyNoInteractions(parents, children, students, teachers);
    }
}
