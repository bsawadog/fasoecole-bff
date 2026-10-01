package org.afritechinnovations.service.academic;

import org.afritechinnovations.dto.academic.OwnerGradeDto;
import org.afritechinnovations.model.academic.AcademicYear;
import org.afritechinnovations.model.academic.ClassSubjectTeacher;
import org.afritechinnovations.model.academic.Evaluation;
import org.afritechinnovations.model.academic.Grade;
import org.afritechinnovations.model.academic.GradeHistory;
import org.afritechinnovations.model.academic.GradePeriod;
import org.afritechinnovations.model.academic.GradePeriodStatus;
import org.afritechinnovations.model.academic.Level;
import org.afritechinnovations.model.academic.SchoolClass;
import org.afritechinnovations.model.academic.Subject;
import org.afritechinnovations.model.common.School;
import org.afritechinnovations.model.common.User;
import org.afritechinnovations.model.people.EnrollmentStatus;
import org.afritechinnovations.model.people.Student;
import org.afritechinnovations.model.people.StudentEnrollment;
import org.afritechinnovations.model.people.Teacher;
import org.afritechinnovations.repository.academic.AcademicYearRepository;
import org.afritechinnovations.repository.academic.AttendanceRepository;
import org.afritechinnovations.repository.academic.ClassSubjectTeacherRepository;
import org.afritechinnovations.repository.academic.EvaluationRepository;
import org.afritechinnovations.repository.academic.GradeHistoryRepository;
import org.afritechinnovations.repository.academic.GradePeriodRepository;
import org.afritechinnovations.repository.academic.GradeRepository;
import org.afritechinnovations.repository.academic.ReportCardRepository;
import org.afritechinnovations.repository.academic.SchoolClassRepository;
import org.afritechinnovations.repository.common.SchoolRepository;
import org.afritechinnovations.repository.common.UserRepository;
import org.afritechinnovations.repository.people.StudentEnrollmentRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.security.access.AccessDeniedException;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class OwnerGradeServiceTest {

    private static final Long OWNER_ID = 10L;

    @Mock SchoolRepository schoolRepository;
    @Mock AcademicYearRepository academicYearRepository;
    @Mock SchoolClassRepository schoolClassRepository;
    @Mock ClassSubjectTeacherRepository classSubjectTeacherRepository;
    @Mock StudentEnrollmentRepository studentEnrollmentRepository;
    @Mock GradePeriodRepository gradePeriodRepository;
    @Mock EvaluationRepository evaluationRepository;
    @Mock GradeRepository gradeRepository;
    @Mock GradeHistoryRepository gradeHistoryRepository;
    @Mock ReportCardRepository reportCardRepository;
    @Mock AttendanceRepository attendanceRepository;
    @Mock UserRepository userRepository;
    @InjectMocks OwnerGradeService service;

    private School school;
    private AcademicYear year;
    private SchoolClass sixA;
    private GradePeriod term1;
    private ClassSubjectTeacher maths;
    private ClassSubjectTeacher french;
    private Evaluation devoir;
    private Evaluation interro;
    private Student awa;
    private Student issa;

    @BeforeEach
    void setUp() {
        service.setClock(Clock.fixed(LocalDate.of(2026, 11, 1).atStartOfDay().toInstant(ZoneOffset.UTC), ZoneOffset.UTC));
        school = School.builder().id(1L).name("École ABC").owner(User.builder().id(OWNER_ID).build()).build();
        year = AcademicYear.builder().id(2L).school(school).label("2026-2027")
                .startDate(LocalDate.of(2026, 9, 1)).endDate(LocalDate.of(2027, 6, 30)).build();
        sixA = SchoolClass.builder().id(3L).name("6e A").school(school).academicYear(year)
                .level(Level.builder().id(4L).name("6e").build()).build();
        term1 = GradePeriod.builder().id(5L).school(school).academicYear(year).code("TERM1").name("1er trimestre")
                .startDate(LocalDate.of(2026, 9, 1)).endDate(LocalDate.of(2026, 12, 20)).build();
        Teacher teacher = Teacher.builder().id(8L).school(school)
                .user(User.builder().id(80L).firstName("Paul").lastName("Ouédraogo").build()).build();
        maths = ClassSubjectTeacher.builder().id(31L).schoolClass(sixA).teacher(teacher)
                .subject(Subject.builder().id(41L).name("Mathématiques").school(school).build())
                .coefficient(new BigDecimal("2")).build();
        french = ClassSubjectTeacher.builder().id(32L).schoolClass(sixA).teacher(teacher)
                .subject(Subject.builder().id(42L).name("Français").school(school).build())
                .coefficient(BigDecimal.ONE).build();
        devoir = evaluation(51L, maths, "Devoir 1", "20", "1");
        interro = evaluation(52L, maths, "Interrogation", "10", "2");
        awa = student(20L, "Awa", "Kaboré");
        issa = student(21L, "Issa", "Zongo");

        when(schoolRepository.findById(1L)).thenReturn(Optional.of(school));
        when(schoolClassRepository.findById(3L)).thenReturn(Optional.of(sixA));
        when(gradePeriodRepository.findById(5L)).thenReturn(Optional.of(term1));
        when(evaluationRepository.findById(51L)).thenReturn(Optional.of(devoir));
        when(classSubjectTeacherRepository.findAllWithTeacherAndSubjectByClassId(3L)).thenReturn(List.of(maths, french));
        when(studentEnrollmentRepository.findActiveStudentsWithUserByClassId(3L, EnrollmentStatus.ACTIVE))
                .thenReturn(List.of(enrollment(issa), enrollment(awa)));
        when(userRepository.findById(OWNER_ID)).thenReturn(Optional.of(
                User.builder().id(OWNER_ID).firstName("Mariam").lastName("Traoré").build()));
    }

    @Test
    void resultsUseEvaluationWeightsAndSubjectCoefficients() {
        when(gradeRepository.findForClassAndTerm(3L, "TERM1")).thenReturn(List.of(
                grade(awa, maths, devoir, "16", "20"),
                grade(awa, maths, interro, "7", "10"),
                grade(awa, french, null, "10", "20"),
                grade(issa, maths, devoir, "8", "20"),
                grade(issa, french, null, "9", "20")));

        OwnerGradeDto.ClassResults results = service.classResults(3L, 5L, OWNER_ID, false);

        OwnerGradeDto.StudentResult first = results.students().get(0);
        assertEquals("Awa Kaboré", first.fullName());
        // Maths : (16×1 + 14×2) / 3 = 14,67 ; générale : (14,67×2 + 10×1) / 3 = 13,11
        assertEquals(new BigDecimal("14.67"), first.subjectAverages().get(41L));
        assertEquals(new BigDecimal("13.11"), first.average());
        assertEquals(1, first.rank());
        assertEquals("Assez bien", first.mention());
        OwnerGradeDto.StudentResult second = results.students().get(1);
        assertEquals(new BigDecimal("8.33"), second.average());
        assertEquals(2, second.rank());
        assertEquals("Insuffisant", second.mention());
        assertEquals(new BigDecimal("10.72"), results.stats().classAverage());
        assertEquals(new BigDecimal("50.0"), results.stats().passRate());
        assertEquals(List.of("Français", "Mathématiques"),
                results.subjects().stream().map(OwnerGradeDto.SubjectColumn::subjectName).toList());
    }

    @Test
    void modifyingAnExistingGradeRequiresAReasonAndIsTraced() {
        Grade existing = grade(awa, maths, devoir, "16", "20");
        when(gradeRepository.findByEvaluationId(51L)).thenReturn(new ArrayList<>(List.of(existing)));
        List<OwnerGradeDto.GradeEntry> entries = List.of(
                new OwnerGradeDto.GradeEntry(20L, new BigDecimal("15")),
                new OwnerGradeDto.GradeEntry(21L, new BigDecimal("12")));

        assertThrows(IllegalArgumentException.class, () -> service.saveGrades(51L,
                new OwnerGradeDto.SaveGradesRequest(entries, " "), OWNER_ID, false));
        verify(gradeHistoryRepository, never()).save(any());

        OwnerGradeDto.SaveGradesResult result = service.saveGrades(51L,
                new OwnerGradeDto.SaveGradesRequest(entries, "Erreur de report"), OWNER_ID, false);

        assertEquals(1, result.created());
        assertEquals(1, result.updated());
        assertEquals(new BigDecimal("15.00"), existing.getValue());
        ArgumentCaptor<GradeHistory> captor = ArgumentCaptor.forClass(GradeHistory.class);
        verify(gradeHistoryRepository, times(2)).save(captor.capture());
        GradeHistory update = captor.getAllValues().stream().filter(h -> h.getAction().equals("UPDATE")).findFirst()
                .orElseThrow();
        assertEquals(new BigDecimal("16"), update.getOldValue());
        assertEquals(new BigDecimal("15.00"), update.getNewValue());
        assertEquals("Erreur de report", update.getReason());
        assertEquals("Mariam Traoré", update.getChangedByName());
        GradeHistory create = captor.getAllValues().stream().filter(h -> h.getAction().equals("CREATE")).findFirst()
                .orElseThrow();
        assertNull(create.getOldValue());
    }

    @Test
    void gradesOutsideTheScaleAreRejected() {
        when(gradeRepository.findByEvaluationId(51L)).thenReturn(List.of());

        assertThrows(IllegalArgumentException.class, () -> service.saveGrades(51L,
                new OwnerGradeDto.SaveGradesRequest(List.of(
                        new OwnerGradeDto.GradeEntry(20L, new BigDecimal("21"))), null), OWNER_ID, false));
    }

    @Test
    void lockedPeriodRefusesGradeChanges() {
        term1.setStatus(GradePeriodStatus.LOCKED);

        assertThrows(IllegalArgumentException.class, () -> service.saveGrades(51L,
                new OwnerGradeDto.SaveGradesRequest(List.of(
                        new OwnerGradeDto.GradeEntry(20L, new BigDecimal("12"))), null), OWNER_ID, false));
        verify(gradeRepository, never()).save(any());
    }

    @Test
    void anotherOwnerCannotReadTheGrades() {
        assertThrows(AccessDeniedException.class, () -> service.classResults(3L, 5L, 99L, false));
        assertThrows(AccessDeniedException.class, () -> service.gradeSheet(51L, 99L, false));
    }

    @Test
    void defaultTrimestersSplitTheAcademicYear() {
        when(academicYearRepository.findById(2L)).thenReturn(Optional.of(year));
        when(gradePeriodRepository.save(any(GradePeriod.class))).thenAnswer(inv -> inv.getArgument(0));

        List<OwnerGradeDto.PeriodInfo> created = service.createDefaultPeriods(1L,
                new OwnerGradeDto.DefaultPeriodsRequest(2L, "trimestre"), OWNER_ID, false);

        assertEquals(3, created.size());
        assertEquals("TERM1", created.get(0).code());
        assertEquals(LocalDate.of(2026, 9, 1), created.get(0).startDate());
        assertEquals(created.get(0).endDate().plusDays(1), created.get(1).startDate());
        assertEquals(LocalDate.of(2027, 6, 30), created.get(2).endDate());
    }

    private Evaluation evaluation(Long id, ClassSubjectTeacher cst, String title, String max, String weight) {
        return Evaluation.builder().id(id).classSubjectTeacher(cst).period(term1).title(title).type("DEVOIR")
                .evalDate(LocalDate.of(2026, 10, 10)).maxValue(new BigDecimal(max)).weight(new BigDecimal(weight))
                .build();
    }

    private Grade grade(Student student, ClassSubjectTeacher cst, Evaluation evaluation, String value, String max) {
        return Grade.builder().student(student).classSubjectTeacher(cst).evaluation(evaluation).term("TERM1")
                .type("DEVOIR").value(new BigDecimal(value)).maxValue(new BigDecimal(max)).build();
    }

    private Student student(Long id, String firstName, String lastName) {
        return Student.builder().id(id).school(school).registrationNumber("M-" + id)
                .user(User.builder().id(id + 100).firstName(firstName).lastName(lastName).build())
                .build();
    }

    private StudentEnrollment enrollment(Student student) {
        return StudentEnrollment.builder().student(student).schoolClass(sixA)
                .enrollmentDate(LocalDate.of(2026, 9, 1)).build();
    }
}
