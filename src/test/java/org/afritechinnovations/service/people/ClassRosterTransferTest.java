package org.afritechinnovations.service.people;

import org.afritechinnovations.model.academic.AcademicYear;
import org.afritechinnovations.model.academic.SchoolClass;
import org.afritechinnovations.model.common.School;
import org.afritechinnovations.model.common.User;
import org.afritechinnovations.model.people.EnrollmentStatus;
import org.afritechinnovations.model.people.Student;
import org.afritechinnovations.model.people.StudentEnrollment;
import org.afritechinnovations.repository.academic.ClassSubjectTeacherRepository;
import org.afritechinnovations.repository.academic.SchoolClassRepository;
import org.afritechinnovations.repository.people.ParentStudentRepository;
import org.afritechinnovations.repository.people.StudentEnrollmentRepository;
import org.afritechinnovations.security.SchoolPermissions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ClassRosterTransferTest {

    @Mock SchoolClassRepository classes;
    @Mock StudentEnrollmentRepository enrollments;
    @Mock ParentStudentRepository parentStudents;
    @Mock ClassSubjectTeacherRepository classTeachers;
    @Mock SchoolPermissions permissions;
    @Mock org.afritechinnovations.service.auth.EmailVerificationService invitations;
    @InjectMocks ClassRosterService service;

    private final User owner = User.builder().id(10L).build();
    private final School school = School.builder().id(1L).owner(owner).build();
    private final AcademicYear year = AcademicYear.builder().id(5L).build();
    private final SchoolClass cm1A = SchoolClass.builder().id(100L).name("CM1 A").school(school).academicYear(year).build();
    private final SchoolClass cm1B = SchoolClass.builder().id(101L).name("CM1 B").school(school).academicYear(year)
            .capacity(30).build();
    private final Student student = Student.builder().id(7L).school(school).registrationNumber("M-7")
            .user(User.builder().id(70L).firstName("Ali").lastName("Kabore").email("ali@ecole.bf").build()).build();

    private StudentEnrollment activeIn(SchoolClass schoolClass) {
        return StudentEnrollment.builder().id(50L).student(student).schoolClass(schoolClass)
                .academicYear(year).status(EnrollmentStatus.ACTIVE).build();
    }

    @Test
    void transferKeepsStudentRecordAndArchivesPreviousEnrollment() {
        StudentEnrollment current = activeIn(cm1A);
        when(classes.findById(100L)).thenReturn(Optional.of(cm1A));
        when(classes.findById(101L)).thenReturn(Optional.of(cm1B));
        when(enrollments.findByStudentIdAndSchoolClassIdAndStatus(7L, 100L, EnrollmentStatus.ACTIVE))
                .thenReturn(List.of(current));
        when(enrollments.findBySchoolClassIdAndStatus(101L, EnrollmentStatus.ACTIVE)).thenReturn(List.of());
        when(parentStudents.findByStudentIdWithParentUser(7L)).thenReturn(List.of());
        when(classTeachers.findAllWithTeacherByClassId(101L)).thenReturn(List.of());

        var row = service.transferStudent(100L, 7L, 101L, 10L, false);

        assertEquals(7L, row.studentId());
        assertEquals(EnrollmentStatus.TRANSFERRED, current.getStatus());
        assertNotNull(current.getDecidedAt());
        ArgumentCaptor<StudentEnrollment> saved = ArgumentCaptor.forClass(StudentEnrollment.class);
        verify(enrollments, times(2)).save(saved.capture());
        StudentEnrollment created = saved.getAllValues().get(1);
        assertSame(student, created.getStudent());
        assertSame(cm1B, created.getSchoolClass());
        assertSame(year, created.getAcademicYear());
        assertEquals(EnrollmentStatus.ACTIVE, created.getStatus());
    }

    @Test
    void refusesFullTargetClass() {
        cm1B.setCapacity(1);
        when(classes.findById(100L)).thenReturn(Optional.of(cm1A));
        when(classes.findById(101L)).thenReturn(Optional.of(cm1B));
        when(enrollments.findByStudentIdAndSchoolClassIdAndStatus(7L, 100L, EnrollmentStatus.ACTIVE))
                .thenReturn(List.of(activeIn(cm1A)));
        when(enrollments.findBySchoolClassIdAndStatus(101L, EnrollmentStatus.ACTIVE))
                .thenReturn(List.of(StudentEnrollment.builder().build()));

        var error = assertThrows(IllegalArgumentException.class, () -> service.transferStudent(100L, 7L, 101L, 10L, false));
        assertTrue(error.getMessage().contains("complète"));
        verify(enrollments, never()).save(any());
    }

    @Test
    void allowsClassOfAnotherYearUsingTargetYear() {
        AcademicYear nextYearRef = AcademicYear.builder().id(6L).label("2027-2028").build();
        SchoolClass nextYear = SchoolClass.builder().id(200L).name("CM2 A").school(school)
                .academicYear(nextYearRef).build();
        when(classes.findById(100L)).thenReturn(Optional.of(cm1A));
        when(classes.findById(200L)).thenReturn(Optional.of(nextYear));
        when(enrollments.findByStudentIdAndSchoolClassIdAndStatus(7L, 100L, EnrollmentStatus.ACTIVE))
                .thenReturn(List.of(activeIn(cm1A)));
        when(enrollments.findByStudentIdAndAcademicYearId(7L, 6L)).thenReturn(List.of());
        when(parentStudents.findByStudentIdWithParentUser(7L)).thenReturn(List.of());
        when(classTeachers.findAllWithTeacherByClassId(200L)).thenReturn(List.of());

        service.transferStudent(100L, 7L, 200L, 10L, false);

        ArgumentCaptor<StudentEnrollment> saved = ArgumentCaptor.forClass(StudentEnrollment.class);
        verify(enrollments, times(2)).save(saved.capture());
        assertSame(nextYearRef, saved.getAllValues().get(1).getAcademicYear());
    }

    @Test
    void refusesWhenAlreadyActiveInTargetYear() {
        AcademicYear nextYearRef = AcademicYear.builder().id(6L).label("2027-2028").build();
        SchoolClass nextYear = SchoolClass.builder().id(200L).name("CM2 A").school(school)
                .academicYear(nextYearRef).build();
        SchoolClass nextYearOther = SchoolClass.builder().id(201L).name("CM2 B").school(school)
                .academicYear(nextYearRef).build();
        when(classes.findById(100L)).thenReturn(Optional.of(cm1A));
        when(classes.findById(200L)).thenReturn(Optional.of(nextYear));
        when(enrollments.findByStudentIdAndSchoolClassIdAndStatus(7L, 100L, EnrollmentStatus.ACTIVE))
                .thenReturn(List.of(activeIn(cm1A)));
        when(enrollments.findByStudentIdAndAcademicYearId(7L, 6L)).thenReturn(List.of(
                StudentEnrollment.builder().schoolClass(nextYearOther).status(EnrollmentStatus.ACTIVE).build()));

        var error = assertThrows(IllegalArgumentException.class, () -> service.transferStudent(100L, 7L, 200L, 10L, false));
        assertTrue(error.getMessage().contains("CM2 B"));
        verify(enrollments, never()).save(any());
    }

    @Test
    void refusesStudentNotActiveInSourceClass() {
        when(classes.findById(100L)).thenReturn(Optional.of(cm1A));
        when(classes.findById(101L)).thenReturn(Optional.of(cm1B));
        when(enrollments.findByStudentIdAndSchoolClassIdAndStatus(7L, 100L, EnrollmentStatus.ACTIVE))
                .thenReturn(List.of());

        assertThrows(IllegalArgumentException.class, () -> service.transferStudent(100L, 7L, 101L, 10L, false));
        verify(enrollments, never()).save(any());
    }

    @Test
    void refusesClassOfAnotherOwner() {
        School otherSchool = School.builder().id(2L).owner(User.builder().id(99L).build()).build();
        SchoolClass foreign = SchoolClass.builder().id(300L).school(otherSchool).academicYear(year).build();
        when(classes.findById(100L)).thenReturn(Optional.of(cm1A));
        when(classes.findById(300L)).thenReturn(Optional.of(foreign));

        assertThrows(AccessDeniedException.class, () -> service.transferStudent(100L, 7L, 300L, 10L, false));
        verify(enrollments, never()).save(any());
    }
}
