package org.afritechinnovations.service.people;

import org.afritechinnovations.dto.people.CreateTeacherRequest;
import org.afritechinnovations.dto.people.InitialTeacherSlotRequest;
import org.afritechinnovations.model.academic.*;
import org.afritechinnovations.model.common.*;
import org.afritechinnovations.model.people.*;
import org.afritechinnovations.repository.academic.*;
import org.afritechinnovations.repository.common.*;
import org.afritechinnovations.repository.people.*;
import org.afritechinnovations.service.auth.EmailVerificationService;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.crypto.password.PasswordEncoder;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class TeacherEmploymentCreationTest {
    @Mock SchoolRepository schools;
    @Mock SchoolClassRepository classes;
    @Mock SubjectRepository subjects;
    @Mock UserRepository users;
    @Mock RoleRepository roles;
    @Mock SchoolUserRepository memberships;
    @Mock PasswordEncoder encoder;
    @Mock TeacherRepository teachers;
    @Mock TeacherRateRepository rates;
    @Mock TeacherScheduleSlotRepository slots;
    @Mock ClassSubjectTeacherRepository assignments;
    @Mock EmailVerificationService invitations;
    @InjectMocks TeacherWorkService service;
    @Mock org.afritechinnovations.repository.finance.PayrollTaxRepository payrollTaxes;

    final School school = School.builder().id(1L).owner(User.builder().id(10L).build()).build();
    final AcademicYear year = AcademicYear.builder().id(2L).startDate(LocalDate.of(2026, 9, 1))
            .endDate(LocalDate.of(2027, 7, 31)).isCurrent(true).build();
    final SchoolClass schoolClass = SchoolClass.builder().id(11L).name("CM1").school(school).academicYear(year).build();
    final Subject subject = Subject.builder().id(8L).name("Maths").school(school).build();

    @BeforeEach void setup() {
        service.setClock(Clock.fixed(Instant.parse("2026-10-15T12:00:00Z"), ZoneOffset.UTC));
        when(schools.findById(1L)).thenReturn(Optional.of(school));
        when(classes.findById(11L)).thenReturn(Optional.of(schoolClass));
        when(subjects.findById(8L)).thenReturn(Optional.of(subject));
        when(roles.findByName("TEACHER")).thenReturn(Optional.of(Role.builder().name("TEACHER").build()));
        when(users.save(any())).thenAnswer(call -> { User user = call.getArgument(0); user.setId(7L); return user; });
        when(teachers.save(any())).thenAnswer(call -> { Teacher teacher = call.getArgument(0); teacher.setId(9L); return teacher; });
        when(encoder.encode(anyString())).thenReturn("random-hash");
    }

    CreateTeacherRequest request(String mode) {
        var request = new CreateTeacherRequest();
        request.setFirstName("Awa"); request.setLastName("Diallo"); request.setEmail("awa@test.bf");
        request.setRateType(mode); request.setRateEffectiveFrom(LocalDate.of(2026, 10, 1));
        if ("HOURLY".equals(mode)) {
            request.setHourlyRate(new BigDecimal("2500.00"));
            request.setInitialSlots(List.of(slot(1, "08:00", "10:00")));
        } else request.setMonthlySalary(new BigDecimal("95000.00"));
        return request;
    }

    InitialTeacherSlotRequest slot(int day, String start, String end) {
        var slot = new InitialTeacherSlotRequest();
        slot.setClassId(11L); slot.setSubjectId(8L); slot.setDayOfWeek(day);
        slot.setStartTime(start); slot.setEndTime(end); slot.setEffectiveFrom(LocalDate.of(2026, 10, 15));
        return slot;
    }

    @Test void createsHourlyRateAssignmentsAndScheduleWithOneInvitation() {
        var request = request("HOURLY");
        request.setInitialSlots(List.of(slot(1, "08:00", "10:00"), slot(3, "09:00", "11:00")));
        var result = service.createSchoolTeacher(1L, request, 10L, false);
        verify(rates).save(argThat(rate -> rate.getRateType() == TeacherRateType.HOURLY
                && rate.getAmount().compareTo(new BigDecimal("2500")) == 0 && rate.getEffectiveFrom().equals(LocalDate.of(2026, 10, 1))));
        verify(teachers).save(argThat(teacher -> teacher.getMonthlySalary() == null));
        verify(assignments, times(1)).save(argThat(assignment -> assignment.getSchoolClass() == schoolClass && assignment.getSubject() == subject));
        verify(slots, times(2)).save(any());
        verify(invitations, times(1)).sendAccessAssigned(any(), eq(school), eq("Enseignant"));
        assertEquals(1, result.classCount()); assertEquals(List.of("Maths"), result.subjects());
    }

    @Test void createsFixedMonthlySalaryWithoutRequiringSchedule() {
        service.createSchoolTeacher(1L, request("FIXED_MONTHLY"), 10L, false);
        verify(teachers).save(argThat(teacher -> teacher.getMonthlySalary().compareTo(new BigDecimal("95000")) == 0));
        verify(rates).save(argThat(rate -> rate.getRateType() == TeacherRateType.FIXED_MONTHLY));
        verifyNoInteractions(slots, assignments);
    }

    @Test void overlappingSlotsAreRejectedBeforeCreatingAnyAccountOrSendingEmail() {
        var request = request("HOURLY");
        request.setInitialSlots(List.of(slot(1, "08:00", "10:00"), slot(1, "09:30", "11:00")));
        assertThrows(IllegalArgumentException.class, () -> service.createSchoolTeacher(1L, request, 10L, false));
        verifyNoInteractions(users, teachers, rates, slots, assignments, invitations);
    }

    @Test void adjacentSlotsAreAllowedAndOnlyOneClassAssignmentIsCreated() {
        var request = request("HOURLY");
        request.setInitialSlots(List.of(slot(1, "08:00", "10:00"), slot(1, "10:00", "11:00")));
        service.createSchoolTeacher(1L, request, 10L, false);
        verify(slots, times(2)).save(any()); verify(assignments, times(1)).save(any());
    }

    @Test void rejectsClassesFromOtherSchoolsAndNeverCreatesAnOrphanAccount() {
        schoolClass.setSchool(School.builder().id(99L).build());
        assertThrows(AccessDeniedException.class, () -> service.createSchoolTeacher(1L, request("HOURLY"), 10L, false));
        verifyNoInteractions(users, teachers, invitations);
    }

    @Test void refusesBothSalaryModes() {
        var request = request("HOURLY");
        request.setMonthlySalary(new BigDecimal("90000"));
        assertThrows(IllegalArgumentException.class, () -> service.createSchoolTeacher(1L, request, 10L, false));
        verifyNoInteractions(users, teachers, invitations);
    }

    @Test void canRecordSeveralTeachingSubjectsWithoutClassOrSlotsEvenForHourlyPay() {
        var request = request("HOURLY");
        request.setInitialSlots(List.of());
        Subject french = Subject.builder().id(9L).name("Français").school(school).build();
        when(subjects.findById(9L)).thenReturn(Optional.of(french));
        request.setSubjectIds(List.of(8L, 9L, 8L));
        var result = service.createSchoolTeacher(1L, request, 10L, false);
        assertEquals(0, result.classCount());
        assertEquals(2, result.teachingSubjects().size());
        verifyNoInteractions(slots, assignments);
        verify(invitations, times(1)).sendAccessAssigned(any(), eq(school), eq("Enseignant"));
    }

    @Test void createsTwoSubjectsInOneClassWithoutDuplicatingAnAssignmentFromTheSchedule() {
        var request = request("HOURLY");
        Subject french = Subject.builder().id(9L).name("Français").school(school).build();
        when(subjects.findById(9L)).thenReturn(Optional.of(french));
        request.setClassId(11L); request.setSubjectIds(List.of(8L, 9L));
        var result = service.createSchoolTeacher(1L, request, 10L, false);
        verify(assignments, times(2)).save(any());
        verify(slots, times(1)).save(any());
        assertEquals(1, result.classCount());
    }

    @Test void rejectsAnInvalidSubjectBeforeCreatingAnAccountOrAnyAssignment() {
        var request = request("FIXED_MONTHLY");
        Subject foreign = Subject.builder().id(9L).name("Français").school(School.builder().id(99L).build()).build();
        when(subjects.findById(9L)).thenReturn(Optional.of(foreign));
        request.setClassId(11L); request.setSubjectIds(List.of(8L, 9L));
        assertThrows(AccessDeniedException.class, () -> service.createSchoolTeacher(1L, request, 10L, false));
        verifyNoInteractions(users, teachers, assignments, slots, invitations);
    }

    @Test void assignsSeveralSubjectsToAnExistingTeacherAtomicallyAndKeepsExistingSubjects() {
        Subject french = Subject.builder().id(9L).name("Français").school(school).build();
        when(subjects.findById(9L)).thenReturn(Optional.of(french));
        Teacher teacher = Teacher.builder().id(9L).school(school).user(User.builder().id(7L).build()).build();
        when(teachers.findById(9L)).thenReturn(Optional.of(teacher));
        var existing = ClassSubjectTeacher.builder().teacher(teacher).schoolClass(schoolClass).subject(subject).build();
        when(assignments.findBySchoolClassIdAndSubjectIdAndTeacherId(11L, 8L, 9L)).thenReturn(Optional.of(existing));
        var request = new org.afritechinnovations.dto.people.AssignClassTeacherRequest();
        request.setTeacherId(9L); request.setSubjectIds(List.of(8L, 9L, 9L));
        service.assignClassTeacher(11L, request, 10L, false);
        verify(assignments, times(1)).save(argThat(item -> item.getSubject() == french));
        assertEquals(2, teacher.getTeachingSubjects().size());
        verify(invitations, never()).sendAccessAssigned(any(), any(), any());
    }

    @Test void refusesSlotsOutsideTheAcademicYearOrBeforeTheRateTakesEffect() {
        var request = request("HOURLY");
        request.getInitialSlots().getFirst().setEffectiveFrom(LocalDate.of(2028, 1, 1));
        assertThrows(IllegalArgumentException.class, () -> service.createSchoolTeacher(1L, request, 10L, false));
        request.getInitialSlots().getFirst().setEffectiveFrom(LocalDate.of(2026, 9, 1));
        assertThrows(IllegalArgumentException.class, () -> service.createSchoolTeacher(1L, request, 10L, false));
        verifyNoInteractions(users, teachers, invitations);
    }
}
