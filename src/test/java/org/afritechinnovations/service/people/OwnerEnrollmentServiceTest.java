package org.afritechinnovations.service.people;

import org.afritechinnovations.dto.people.NewStudentEnrollmentRequest;
import org.afritechinnovations.dto.people.OwnerEnrollmentDto;
import org.afritechinnovations.model.academic.AcademicYear;
import org.afritechinnovations.model.academic.Level;
import org.afritechinnovations.model.academic.SchoolClass;
import org.afritechinnovations.model.common.School;
import org.afritechinnovations.model.common.SchoolType;
import org.afritechinnovations.model.common.StaffModule;
import org.afritechinnovations.model.common.User;
import org.afritechinnovations.model.people.EnrollmentDecision;
import org.afritechinnovations.model.people.EnrollmentStatus;
import org.afritechinnovations.model.people.Student;
import org.afritechinnovations.model.people.StudentEnrollment;
import org.afritechinnovations.repository.academic.AcademicYearRepository;
import org.afritechinnovations.repository.academic.ClassSubjectTeacherRepository;
import org.afritechinnovations.repository.academic.GradePeriodRepository;
import org.afritechinnovations.repository.academic.LevelRepository;
import org.afritechinnovations.repository.academic.SchoolClassRepository;
import org.afritechinnovations.repository.common.SchoolRepository;
import org.afritechinnovations.repository.people.StudentEnrollmentRepository;
import org.afritechinnovations.security.SchoolPermissions;
import org.afritechinnovations.service.academic.OwnerGradeService;
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

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class OwnerEnrollmentServiceTest {

    private static final Long OWNER_ID = 10L;

    @Mock SchoolPermissions permissions;
    @Mock SchoolRepository schoolRepository;
    @Mock AcademicYearRepository academicYearRepository;
    @Mock SchoolClassRepository schoolClassRepository;
    @Mock LevelRepository levelRepository;
    @Mock ClassSubjectTeacherRepository classSubjectTeacherRepository;
    @Mock GradePeriodRepository gradePeriodRepository;
    @Mock StudentEnrollmentRepository enrollmentRepository;
    @Mock OwnerGradeService gradeService;
    @Mock ClassRosterService classRosterService;
    @Mock org.afritechinnovations.repository.finance.FeeTypeRepository feeTypeRepository;
    @Mock org.afritechinnovations.repository.people.ParentRepository parentRepository;
    @Mock org.afritechinnovations.repository.people.ParentStudentRepository parentStudentRepository;

    @InjectMocks OwnerEnrollmentService service;

    private School school;
    private AcademicYear y2026;
    private AcademicYear y2027;
    private Level cp1;
    private Level cp2;
    private SchoolClass cp1a2026;
    private SchoolClass cp1a2027;
    private SchoolClass cp2a2027;
    private StudentEnrollment awa;
    private StudentEnrollment issa;
    private final List<StudentEnrollment> saved = new ArrayList<>();

    @BeforeEach
    void setUp() {
        school = School.builder().id(1L).name("École ABC").type(SchoolType.PRIMAIRE)
                .owner(User.builder().id(OWNER_ID).build()).build();
        y2026 = AcademicYear.builder().id(1L).school(school).label("2025-2026")
                .startDate(LocalDate.of(2025, 10, 1)).endDate(LocalDate.of(2026, 7, 30)).isCurrent(true).build();
        y2027 = AcademicYear.builder().id(2L).school(school).label("2026-2027")
                .startDate(LocalDate.of(2026, 10, 1)).endDate(LocalDate.of(2027, 7, 30)).isCurrent(false).build();
        cp1 = Level.builder().id(1L).school(school).name("CP1").cycle("PRIMAIRE").orderIndex(1).build();
        cp2 = Level.builder().id(2L).school(school).name("CP2").cycle("PRIMAIRE").orderIndex(2).build();
        cp1a2026 = SchoolClass.builder().id(11L).school(school).academicYear(y2026).level(cp1).name("CP1-A").capacity(40).build();
        cp1a2027 = SchoolClass.builder().id(21L).school(school).academicYear(y2027).level(cp1).name("CP1-A").capacity(40).build();
        cp2a2027 = SchoolClass.builder().id(22L).school(school).academicYear(y2027).level(cp2).name("CP2-A").capacity(1).build();
        awa = enrollment(101L, 201L, "Awa");
        issa = enrollment(102L, 202L, "Issa");

        when(schoolRepository.findById(1L)).thenReturn(Optional.of(school));
        when(academicYearRepository.findById(1L)).thenReturn(Optional.of(y2026));
        when(academicYearRepository.findById(2L)).thenReturn(Optional.of(y2027));
        when(levelRepository.findBySchoolIdOrderByOrderIndexAsc(1L)).thenReturn(List.of(cp1, cp2));
        when(schoolClassRepository.findAllWithLevelBySchoolAndYear(1L, 1L)).thenReturn(List.of(cp1a2026));
        when(schoolClassRepository.findAllWithLevelBySchoolAndYear(1L, 2L)).thenReturn(List.of(cp1a2027, cp2a2027));
        when(enrollmentRepository.findByYearWithStudent(1L)).thenReturn(List.of(awa, issa));
        when(enrollmentRepository.findByYearWithStudent(2L)).thenReturn(List.of());
        when(enrollmentRepository.findById(101L)).thenReturn(Optional.of(awa));
        when(enrollmentRepository.findById(102L)).thenReturn(Optional.of(issa));
        when(enrollmentRepository.save(any(StudentEnrollment.class))).thenAnswer(i -> {
            saved.add(i.getArgument(0));
            return i.getArgument(0);
        });
        when(gradeService.annualResults(cp1a2026))
                .thenReturn(new OwnerGradeService.AnnualResults(10.0, Map.of(201L, 13.5, 202L, 8.25)));
    }

    private StudentEnrollment enrollment(Long id, Long studentId, String firstName) {
        Student student = Student.builder().id(studentId).school(school).registrationNumber("M" + studentId)
                .user(User.builder().id(studentId + 1000).firstName(firstName).lastName("Kaboré").build()).build();
        return StudentEnrollment.builder().id(id).student(student).schoolClass(cp1a2026).academicYear(y2026)
                .status(EnrollmentStatus.ACTIVE).build();
    }

    @Test
    void registersNewStudentInChosenClass() {
        when(schoolClassRepository.findById(21L)).thenReturn(Optional.of(cp1a2027));
        when(enrollmentRepository.findBySchoolClassIdAndStatus(21L, EnrollmentStatus.ACTIVE)).thenReturn(List.of());
        var request = newStudent(21L);

        service.registerStudent(1L, request, OWNER_ID, false);

        verify(classRosterService).enrollNewStudentWithFees(cp1a2027, request);
    }

    @Test
    void refusesNewStudentInFullClass() {
        when(schoolClassRepository.findById(22L)).thenReturn(Optional.of(cp2a2027));
        when(enrollmentRepository.findBySchoolClassIdAndStatus(22L, EnrollmentStatus.ACTIVE)).thenReturn(List.of(awa));

        var error = assertThrows(IllegalArgumentException.class,
                () -> service.registerStudent(1L, newStudent(22L), OWNER_ID, false));
        assertTrue(error.getMessage().contains("complète"));
        verifyNoInteractions(classRosterService);
    }

    @Test
    void refusesNewStudentForAnotherSchoolOrUnauthorizedUser() {
        School other = School.builder().id(2L).owner(User.builder().id(99L).build()).build();
        SchoolClass foreign = SchoolClass.builder().id(30L).school(other).academicYear(y2026).level(cp1).name("X").build();
        when(schoolClassRepository.findById(30L)).thenReturn(Optional.of(foreign));

        assertThrows(IllegalArgumentException.class, () -> service.registerStudent(1L, newStudent(30L), OWNER_ID, false));
        assertThrows(AccessDeniedException.class, () -> service.registerStudent(1L, newStudent(21L), 55L, false));
        verifyNoInteractions(classRosterService);
    }

    @Test
    void yearClassesReportActiveHeadcount() {
        StudentEnrollment active = enrollment(103L, 203L, "Sali");
        active.setSchoolClass(cp1a2027);
        active.setAcademicYear(y2027);
        when(enrollmentRepository.findByYearWithStudent(2L)).thenReturn(List.of(active));

        var classes = service.yearClasses(1L, 2L, OWNER_ID, false);

        assertEquals(List.of(21L, 22L), classes.stream().map(OwnerEnrollmentDto.TargetClass::id).toList());
        assertEquals(1L, classes.get(0).enrolled());
        assertEquals(0L, classes.get(1).enrolled());
    }

    @Test
    void refusesExistingParentUnknownToTheOwnerSchools() {
        when(schoolClassRepository.findById(21L)).thenReturn(Optional.of(cp1a2027));
        when(enrollmentRepository.findBySchoolClassIdAndStatus(21L, EnrollmentStatus.ACTIVE)).thenReturn(List.of());
        when(schoolRepository.findByOwnerId(OWNER_ID)).thenReturn(List.of(school));
        var request = newStudent(21L);
        request.setGuardians(List.of(new OwnerEnrollmentDto.Guardian(500L, "X", "Y", null, null, null)));

        assertThrows(AccessDeniedException.class, () -> service.registerStudent(1L, request, OWNER_ID, false));
        verifyNoInteractions(classRosterService);

        when(parentRepository.isKnownInSchools(eq(500L), any())).thenReturn(true);
        service.registerStudent(1L, request, OWNER_ID, false);
        verify(classRosterService).enrollNewStudentWithFees(cp1a2027, request);
    }

    @Test
    void searchesGuardiansAcrossTheOwnerSchoolsOnly() {
        School second = School.builder().id(3L).owner(school.getOwner()).build();
        when(schoolRepository.findByOwnerId(OWNER_ID)).thenReturn(List.of(school, second));
        User moussa = User.builder().id(4L).firstName("Moussa").lastName("Kaboré").email("m@x.bf").build();
        var parent = org.afritechinnovations.model.people.Parent.builder().id(7L).user(moussa).build();
        when(parentRepository.searchInSchools(eq(java.util.Set.of(1L, 3L)), eq("%kab%"), any()))
                .thenReturn(List.of(parent));

        assertTrue(service.searchGuardians(1L, " k", OWNER_ID, false).isEmpty());
        var found = service.searchGuardians(1L, "KAB", OWNER_ID, false);

        assertEquals(1, found.size());
        assertEquals(7L, found.get(0).parentId());
        assertThrows(AccessDeniedException.class, () -> service.searchGuardians(1L, "kab", 55L, false));
    }

    private NewStudentEnrollmentRequest newStudent(Long classId) {
        var request = new NewStudentEnrollmentRequest();
        request.setClassId(classId);
        request.setFirstName("Sali");
        request.setLastName("Ouédraogo");
        request.setEmail("sali@ecole.bf");
        request.setPassword("motdepasse");
        request.setRegistrationNumber("M-300");
        return request;
    }

    @Test
    void planSuggestsPromotionOrRepeatFromTheAnnualAverage() {
        OwnerEnrollmentDto.PromotionPlan plan = service.plan(1L, 1L, 2L, OWNER_ID, false);

        List<OwnerEnrollmentDto.StudentPlan> students = plan.classes().get(0).students();
        OwnerEnrollmentDto.StudentPlan awaPlan = students.stream().filter(s -> s.studentId() == 201L).findFirst().orElseThrow();
        OwnerEnrollmentDto.StudentPlan issaPlan = students.stream().filter(s -> s.studentId() == 202L).findFirst().orElseThrow();
        assertEquals(EnrollmentDecision.PROMOTED, awaPlan.suggestedDecision());
        assertEquals(22L, awaPlan.suggestedClassId());
        assertEquals(EnrollmentDecision.REPEATED, issaPlan.suggestedDecision());
        assertEquals(21L, issaPlan.suggestedClassId());
        assertEquals(2, plan.pending());
    }

    @Test
    void applyClosesTheYearAndReEnrollsWithinCapacity() {
        OwnerEnrollmentDto.PromotionResult result = service.apply(1L, new OwnerEnrollmentDto.PromotionRequest(1L, 2L,
                List.of(new OwnerEnrollmentDto.DecisionItem(101L, EnrollmentDecision.PROMOTED, 22L),
                        new OwnerEnrollmentDto.DecisionItem(102L, EnrollmentDecision.PROMOTED, 22L))),
                OWNER_ID, false);

        assertEquals(1, result.applied());
        assertEquals(1, result.skipped().size());
        assertTrue(result.skipped().get(0).reason().contains("complète"));
        assertEquals(EnrollmentStatus.COMPLETED, awa.getStatus());
        assertEquals(EnrollmentDecision.PROMOTED, awa.getDecision());
        assertEquals(0, awa.getDecisionAverage().compareTo(new java.math.BigDecimal("13.50")));
        assertEquals(EnrollmentStatus.ACTIVE, issa.getStatus());
        StudentEnrollment created = saved.stream().filter(e -> e.getId() == null).findFirst().orElseThrow();
        assertEquals(cp2a2027, created.getSchoolClass());
        assertEquals(y2027, created.getAcademicYear());
    }

    @Test
    void leavingStudentIsClosedWithoutReEnrollment() {
        OwnerEnrollmentDto.PromotionResult result = service.apply(1L, new OwnerEnrollmentDto.PromotionRequest(1L, 2L,
                List.of(new OwnerEnrollmentDto.DecisionItem(102L, EnrollmentDecision.LEFT, null))), OWNER_ID, false);

        assertEquals(1, result.applied());
        assertEquals(EnrollmentDecision.LEFT, issa.getDecision());
        assertEquals(1, saved.size());
    }

    @Test
    void undoRemovesTheNextYearEnrollment() {
        awa.setStatus(EnrollmentStatus.COMPLETED);
        awa.setDecision(EnrollmentDecision.PROMOTED);
        StudentEnrollment next = StudentEnrollment.builder().id(300L).student(awa.getStudent())
                .schoolClass(cp2a2027).academicYear(y2027).status(EnrollmentStatus.ACTIVE).build();
        when(enrollmentRepository.findByStudentId(201L)).thenReturn(List.of(awa, next));

        service.undo(101L, OWNER_ID, false);

        verify(enrollmentRepository).delete(next);
        assertEquals(EnrollmentStatus.ACTIVE, awa.getStatus());
        assertNull(awa.getDecision());
    }

    @Test
    void staffNeedsTheEnrollmentModule() {
        assertThrows(AccessDeniedException.class, () -> service.overview(1L, 99L, false));
        when(permissions.staffAllows(1L, 99L, StaffModule.ENROLLMENT)).thenReturn(true);
        when(academicYearRepository.findBySchoolId(1L)).thenReturn(List.of(y2026, y2027));
        assertEquals(2, service.overview(1L, 99L, false).years().size());
    }

    @Test
    void sectionIsTakenAfterTheLevelName() {
        assertEquals("a", OwnerEnrollmentService.section("CP1-A", "CP1"));
        assertEquals("a", OwnerEnrollmentService.section("6e A", "6e"));
    }
}
