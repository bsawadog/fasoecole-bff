package org.afritechinnovations.service.people;

import org.afritechinnovations.model.academic.*;
import org.afritechinnovations.model.common.*;
import org.afritechinnovations.model.people.*;
import org.afritechinnovations.repository.academic.SchoolClassRepository;
import org.afritechinnovations.repository.people.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class StudentAdmissionTest {
    @Mock SchoolClassRepository classes;
    @Mock StudentRepository students;
    @Mock StudentEnrollmentRepository enrollments;
    @InjectMocks ClassRosterService service;
    final School school = School.builder().id(2L).build();
    final User user = User.builder().id(7L).passwordHash("chosen").build();
    final AcademicYear year = AcademicYear.builder().id(5L).school(school).startDate(LocalDate.of(2026, 9, 1)).build();
    final SchoolClass classroom = SchoolClass.builder().id(11L).school(school).academicYear(year).capacity(30).build();

    @BeforeEach void setup() {
        when(classes.findById(11L)).thenReturn(Optional.of(classroom));
        when(students.save(any())).thenAnswer(i -> { Student s = i.getArgument(0); s.setId(40L); return s; });
    }

    @Test void publicStudentGetsDossierMatriculeYearAndActiveEnrollmentOnExistingAccount() {
        service.attachApprovedStudent(user, school, 11L, null);
        ArgumentCaptor<Student> dossier = ArgumentCaptor.forClass(Student.class);
        verify(students).save(dossier.capture());
        assertSame(user, dossier.getValue().getUser()); assertEquals("MAT-2026-001", dossier.getValue().getRegistrationNumber());
        verify(enrollments).save(argThat(e -> e.getStudent() == dossier.getValue() && e.getSchoolClass() == classroom
                && e.getAcademicYear() == year && e.getStatus() == EnrollmentStatus.ACTIVE));
        assertEquals("chosen", user.getPasswordHash());
    }

    @Test void missingOrForeignClassCannotCreateDossier() {
        assertThrows(IllegalArgumentException.class, () -> service.attachApprovedStudent(user, school, null, null));
        classroom.setSchool(School.builder().id(3L).build());
        assertThrows(IllegalArgumentException.class, () -> service.attachApprovedStudent(user, school, 11L, null));
        verify(students, never()).save(any()); verify(enrollments, never()).save(any());
    }

    @Test void fullClassAndDuplicateMatriculeAreRejected() {
        classroom.setCapacity(0);
        assertThrows(IllegalArgumentException.class, () -> service.attachApprovedStudent(user, school, 11L, "M1"));
        classroom.setCapacity(30);
        when(students.findBySchoolIdAndRegistrationNumber(2L, "M1")).thenReturn(Optional.of(Student.builder().build()));
        assertThrows(IllegalArgumentException.class, () -> service.attachApprovedStudent(user, school, 11L, "M1"));
        verify(students, never()).save(any());
    }

    @Test void reusesDossierAndDoesNotDuplicateExistingActiveEnrollment() {
        Student dossier = Student.builder().id(40L).user(user).school(school).registrationNumber("M1").build();
        when(students.findAllByUserId(7L)).thenReturn(List.of(dossier));
        when(enrollments.findByStudentIdAndAcademicYearId(40L, 5L)).thenReturn(List.of(StudentEnrollment.builder()
                .student(dossier).schoolClass(classroom).academicYear(year).status(EnrollmentStatus.ACTIVE).build()));
        service.attachApprovedStudent(user, school, 11L, null);
        verify(students, never()).save(any()); verify(enrollments, never()).save(any());
    }

    @Test void activeEnrollmentInAnotherClassRequiresExplicitTransfer() {
        Student dossier = Student.builder().id(40L).user(user).school(school).registrationNumber("M1").build();
        when(students.findAllByUserId(7L)).thenReturn(List.of(dossier));
        when(enrollments.findByStudentIdAndAcademicYearId(40L, 5L)).thenReturn(List.of(StudentEnrollment.builder()
                .schoolClass(SchoolClass.builder().id(12L).build()).status(EnrollmentStatus.ACTIVE).build()));
        assertThrows(IllegalArgumentException.class, () -> service.attachApprovedStudent(user, school, 11L, null));
        verify(enrollments, never()).save(any());
    }
}
