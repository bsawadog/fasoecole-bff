package org.afritechinnovations.service.self;

import org.afritechinnovations.model.common.Role;
import org.afritechinnovations.model.common.School;
import org.afritechinnovations.model.common.SchoolUser;
import org.afritechinnovations.model.common.User;
import org.afritechinnovations.model.people.Parent;
import org.afritechinnovations.model.people.ParentStudent;
import org.afritechinnovations.model.people.Student;
import org.afritechinnovations.repository.academic.AttendanceRepository;
import org.afritechinnovations.repository.academic.ClassSubjectTeacherRepository;
import org.afritechinnovations.repository.academic.GradePeriodRepository;
import org.afritechinnovations.repository.academic.GradeRepository;
import org.afritechinnovations.repository.common.SchoolUserRepository;
import org.afritechinnovations.repository.finance.InvoiceRepository;
import org.afritechinnovations.repository.finance.PaymentRepository;
import org.afritechinnovations.repository.people.ParentRepository;
import org.afritechinnovations.repository.people.ParentStudentRepository;
import org.afritechinnovations.repository.people.StudentEnrollmentRepository;
import org.afritechinnovations.repository.people.StudentRepository;
import org.afritechinnovations.repository.people.TeacherScheduleSlotRepository;
import org.afritechinnovations.service.academic.OwnerGradeService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.security.access.AccessDeniedException;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class FamilySpaceServiceTest {
    @Mock StudentRepository students;
    @Mock ParentRepository parents;
    @Mock ParentStudentRepository parentStudents;
    @Mock SchoolUserRepository schoolUsers;
    @Mock StudentEnrollmentRepository enrollments;
    @Mock AttendanceRepository attendances;
    @Mock InvoiceRepository invoices;
    @Mock PaymentRepository payments;
    @Mock GradePeriodRepository periods;
    @Mock GradeRepository grades;
    @Mock TeacherScheduleSlotRepository slots;
    @Mock ClassSubjectTeacherRepository assignments;
    @Mock OwnerGradeService gradeService;
    @InjectMocks FamilySpaceService service;

    private final School schoolA = School.builder().id(1L).name("École A").build();
    private final School schoolB = School.builder().id(2L).name("École B").build();
    private final User parentUser = User.builder().id(7L).firstName("Awa").lastName("Kaboré").build();
    private final Student childA = student(11L, "Moussa", schoolA);
    private final Student childB = student(12L, "Fanta", schoolB);

    @BeforeEach
    void setUp() {
        Parent parent = Parent.builder().id(70L).user(parentUser).build();
        when(parents.findByUserId(7L)).thenReturn(Optional.of(parent));
        when(parentStudents.findChildrenWithUserByParentId(70L)).thenReturn(List.of(
                ParentStudent.builder().parent(parent).student(childA).relationship("Mère").build(),
                ParentStudent.builder().parent(parent).student(childB).relationship("Mère").build()));
        // L'accès à l'école B a été révoqué : seul le lien avec l'école A subsiste.
        when(schoolUsers.findByUserId(7L)).thenReturn(List.of(SchoolUser.builder().user(parentUser).school(schoolA)
                .role(Role.builder().name("PARENT").build()).build()));
        when(students.findAllByUserId(anyLong())).thenReturn(List.of());
        when(enrollments.findByStudentId(anyLong())).thenReturn(List.of());
        when(attendances.findByStudentIdOrderByAttendanceDateDesc(anyLong())).thenReturn(List.of());
        when(invoices.findByStudentId(anyLong())).thenReturn(List.of());
    }

    @Test
    void parentSeesOnlyChildrenOfSchoolsWithActiveAccess() {
        var result = service.students(7L);
        assertEquals(List.of(11L), result.stream().map(s -> s.studentId()).toList());
        assertEquals("Mère", result.get(0).relationship());
    }

    @Test
    void parentCannotOpenTheFileOfARevokedSchoolChild() {
        assertThrows(AccessDeniedException.class, () -> service.grades(7L, 12L));
        assertThrows(AccessDeniedException.class, () -> service.invoices(7L, 12L));
    }

    @Test
    void studentSeesOnlyOwnFile() {
        when(students.findAllByUserId(20L)).thenReturn(List.of(childA));
        when(parents.findByUserId(20L)).thenReturn(Optional.empty());
        assertEquals(1, service.students(20L).size());
        assertDoesNotThrow(() -> service.schedule(20L, 11L));
        assertThrows(AccessDeniedException.class, () -> service.schedule(20L, 12L));
    }

    @Test
    void onlyAGuardianCanActForTheChild() {
        when(parentStudents.findByParentId(70L)).thenReturn(List.of(ParentStudent.builder()
                .parent(Parent.builder().id(70L).user(parentUser).build()).student(childA).build()));
        assertEquals(11L, service.requireGuardedChild(7L, 11L).getId());
        when(students.findAllByUserId(20L)).thenReturn(List.of(childA));
        when(parents.findByUserId(20L)).thenReturn(Optional.empty());
        assertThrows(AccessDeniedException.class, () -> service.requireGuardedChild(20L, 11L));
        assertThrows(AccessDeniedException.class, () -> service.requireGuardedChild(7L, 12L));
    }

    @Test
    void profileListsGuardiansAndSchoolContact() {
        when(parentStudents.findByStudentIdWithParentUser(11L)).thenReturn(List.of(ParentStudent.builder()
                .parent(Parent.builder().id(70L).user(parentUser).build()).student(childA).relationship("Mère").build()));
        var profile = service.profile(7L, 11L);
        assertEquals("Awa Kaboré", profile.guardians().get(0).fullName());
        assertEquals("École A", profile.school().name());
        assertTrue(profile.teachers().isEmpty());
        assertEquals(List.of("École A"), service.parentSchools(7L).stream().map(s -> s.name()).toList());
    }

    private static Student student(Long id, String firstName, School school) {
        return Student.builder().id(id).school(school).registrationNumber("MAT-" + id)
                .user(User.builder().id(id + 100).firstName(firstName).lastName("Kaboré").build()).build();
    }
}
