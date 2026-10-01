package org.afritechinnovations.service.self;

import lombok.RequiredArgsConstructor;
import org.afritechinnovations.dto.academic.OwnerGradeDto;
import org.afritechinnovations.dto.self.SelfServiceDto;
import org.afritechinnovations.model.academic.ClassSubjectTeacher;
import org.afritechinnovations.model.academic.Evaluation;
import org.afritechinnovations.model.academic.SchoolClass;
import org.afritechinnovations.model.common.User;
import org.afritechinnovations.model.people.EnrollmentStatus;
import org.afritechinnovations.model.people.StudentEnrollment;
import org.afritechinnovations.model.people.Teacher;
import org.afritechinnovations.model.people.TeacherScheduleSlot;
import org.afritechinnovations.repository.academic.ClassSubjectTeacherRepository;
import org.afritechinnovations.repository.academic.EvaluationRepository;
import org.afritechinnovations.repository.academic.SchoolClassRepository;
import org.afritechinnovations.repository.people.StudentEnrollmentRepository;
import org.afritechinnovations.repository.people.TeacherRepository;
import org.afritechinnovations.repository.people.TeacherScheduleSlotRepository;
import org.afritechinnovations.service.academic.OwnerGradeService;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Espace enseignant : classes, élèves, emploi du temps et saisie des notes.
 * <p>
 * L'enseignant n'agit que sur les classes où il a une affectation active et sur les évaluations de ses propres
 * matières. Ces contrôles faits, le calcul et la traçabilité des notes sont délégués à {@link OwnerGradeService}
 * (en mode « système », l'auteur enregistré dans l'historique restant l'enseignant).
 */
@Service
@RequiredArgsConstructor
@Transactional
public class TeacherSpaceService {

    private static final List<EnrollmentStatus> ROSTER_STATUSES =
            List.of(EnrollmentStatus.ACTIVE, EnrollmentStatus.COMPLETED);

    private final TeacherRepository teacherRepository;
    private final ClassSubjectTeacherRepository classSubjectTeacherRepository;
    private final SchoolClassRepository schoolClassRepository;
    private final StudentEnrollmentRepository studentEnrollmentRepository;
    private final TeacherScheduleSlotRepository scheduleSlotRepository;
    private final EvaluationRepository evaluationRepository;
    private final OwnerGradeService gradeService;

    // ------------------------------------------------------------------ classes et élèves

    @Transactional(readOnly = true)
    public List<SelfServiceDto.TeacherClass> classes(Long userId) {
        Map<Long, List<ClassSubjectTeacher>> byClass = activeAssignments(userId).stream()
                .collect(Collectors.groupingBy(cst -> cst.getSchoolClass().getId(), LinkedHashMap::new,
                        Collectors.toList()));
        return byClass.values().stream()
                .map(rows -> {
                    SchoolClass cls = rows.get(0).getSchoolClass();
                    List<SelfServiceDto.TeacherSubject> subjects = rows.stream()
                            .map(cst -> new SelfServiceDto.TeacherSubject(cst.getId(), cst.getSubject().getId(),
                                    cst.getSubject().getName()))
                            .sorted(Comparator.comparing(SelfServiceDto.TeacherSubject::subjectName,
                                    String.CASE_INSENSITIVE_ORDER))
                            .toList();
                    return new SelfServiceDto.TeacherClass(cls.getId(), cls.getName(),
                            cls.getLevel() == null ? null : cls.getLevel().getName(), cls.getSchool().getId(),
                            cls.getSchool().getName(), cls.getAcademicYear().getId(),
                            cls.getAcademicYear().getLabel(), Boolean.TRUE.equals(cls.getAcademicYear().getIsCurrent()),
                            roster(cls.getId()).size(), subjects);
                })
                .sorted(Comparator.comparing(SelfServiceDto.TeacherClass::currentYear).reversed()
                        .thenComparing(SelfServiceDto.TeacherClass::schoolName, String.CASE_INSENSITIVE_ORDER)
                        .thenComparing(SelfServiceDto.TeacherClass::className, String.CASE_INSENSITIVE_ORDER))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<SelfServiceDto.RosterStudent> students(Long userId, Long classId) {
        requireTaughtClass(userId, classId);
        return roster(classId).stream()
                .map(se -> new SelfServiceDto.RosterStudent(se.getStudent().getId(), fullName(se.getStudent().getUser()),
                        se.getStudent().getRegistrationNumber(), se.getStudent().getGender(),
                        se.getStudent().getBirthDate()))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<SelfServiceDto.ScheduleEntry> schedule(Long userId) {
        LocalDate today = LocalDate.now();
        List<SelfServiceDto.ScheduleEntry> entries = new java.util.ArrayList<>();
        for (Teacher teacher : teacherRepository.findByUserId(userId)) {
            Map<Long, String> subjectsByClass = classSubjectTeacherRepository
                    .findAllWithSubjectAndClassByTeacherId(teacher.getId()).stream()
                    .filter(ClassSubjectTeacher::isActive)
                    .collect(Collectors.groupingBy(cst -> cst.getSchoolClass().getId(),
                            Collectors.mapping(cst -> cst.getSubject().getName(),
                                    Collectors.collectingAndThen(Collectors.toCollection(java.util.TreeSet::new),
                                            set -> String.join(", ", set)))));
            for (TeacherScheduleSlot slot : scheduleSlotRepository.findAllWithClassByTeacherId(teacher.getId())) {
                if (!isEffective(slot, today)) {
                    continue;
                }
                SchoolClass cls = slot.getSchoolClass();
                entries.add(new SelfServiceDto.ScheduleEntry(slot.getId(), slot.getDayOfWeek(), slot.getStartTime(),
                        slot.getEndTime(), cls.getId(), cls.getName(), cls.getSchool().getName(),
                        subjectsByClass.get(cls.getId()), fullName(teacher.getUser())));
            }
        }
        entries.sort(Comparator.comparingInt(SelfServiceDto.ScheduleEntry::dayOfWeek)
                .thenComparing(SelfServiceDto.ScheduleEntry::startTime));
        return entries;
    }

    // ------------------------------------------------------------------ notes

    @Transactional(readOnly = true)
    public List<OwnerGradeDto.PeriodInfo> periods(Long userId, Long classId) {
        SchoolClass cls = requireTaughtClass(userId, classId);
        return gradeService.listPeriods(cls.getSchool().getId(), userId, true).stream()
                .filter(p -> p.academicYearId().equals(cls.getAcademicYear().getId()))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<OwnerGradeDto.EvaluationInfo> evaluations(Long userId, Long classId, Long periodId) {
        requireTaughtClass(userId, classId);
        Set<Long> mine = assignmentIds(userId);
        return gradeService.listEvaluations(classId, periodId, userId, true).stream()
                .filter(e -> mine.contains(e.classSubjectTeacherId()))
                .toList();
    }

    public OwnerGradeDto.EvaluationInfo createEvaluation(Long userId, Long classId,
                                                         OwnerGradeDto.EvaluationRequest request) {
        requireTaughtClass(userId, classId);
        requireOwnAssignment(userId, request.classSubjectTeacherId());
        return gradeService.createEvaluation(classId, request, userId, true);
    }

    public OwnerGradeDto.EvaluationInfo updateEvaluation(Long userId, Long evaluationId,
                                                         OwnerGradeDto.EvaluationRequest request) {
        requireOwnEvaluation(userId, evaluationId);
        requireOwnAssignment(userId, request.classSubjectTeacherId());
        return gradeService.updateEvaluation(evaluationId, request, userId, true);
    }

    public void deleteEvaluation(Long userId, Long evaluationId, String reason) {
        requireOwnEvaluation(userId, evaluationId);
        gradeService.deleteEvaluation(evaluationId, reason, userId, true);
    }

    @Transactional(readOnly = true)
    public OwnerGradeDto.GradeSheet gradeSheet(Long userId, Long evaluationId) {
        requireOwnEvaluation(userId, evaluationId);
        return gradeService.gradeSheet(evaluationId, userId, true);
    }

    public OwnerGradeDto.SaveGradesResult saveGrades(Long userId, Long evaluationId,
                                                     OwnerGradeDto.SaveGradesRequest request) {
        requireOwnEvaluation(userId, evaluationId);
        return gradeService.saveGrades(evaluationId, request, userId, true);
    }

    // ------------------------------------------------------------------ contrôles d'accès

    private List<ClassSubjectTeacher> activeAssignments(Long userId) {
        return teacherRepository.findByUserId(userId).stream()
                .flatMap(t -> classSubjectTeacherRepository.findAllWithSubjectAndClassByTeacherId(t.getId()).stream())
                .filter(ClassSubjectTeacher::isActive)
                .toList();
    }

    private Set<Long> assignmentIds(Long userId) {
        return activeAssignments(userId).stream().map(ClassSubjectTeacher::getId).collect(Collectors.toSet());
    }

    private SchoolClass requireTaughtClass(Long userId, Long classId) {
        boolean teaches = teacherRepository.findByUserId(userId).stream()
                .anyMatch(t -> classSubjectTeacherRepository.existsBySchoolClassIdAndTeacherIdAndActiveTrue(classId,
                        t.getId()));
        if (!teaches) {
            throw new AccessDeniedException("Vous n'enseignez pas dans cette classe");
        }
        return schoolClassRepository.findById(classId)
                .orElseThrow(() -> new IllegalArgumentException("Classe introuvable : " + classId));
    }

    private void requireOwnAssignment(Long userId, Long classSubjectTeacherId) {
        if (classSubjectTeacherId == null || !assignmentIds(userId).contains(classSubjectTeacherId)) {
            throw new AccessDeniedException("Vous ne pouvez évaluer que vos propres matières");
        }
    }

    private void requireOwnEvaluation(Long userId, Long evaluationId) {
        Evaluation evaluation = evaluationRepository.findById(evaluationId)
                .orElseThrow(() -> new IllegalArgumentException("Évaluation introuvable : " + evaluationId));
        ClassSubjectTeacher cst = evaluation.getClassSubjectTeacher();
        if (!cst.isActive() || !cst.getTeacher().getUser().getId().equals(userId)) {
            throw new AccessDeniedException("Cette évaluation ne relève pas de vos matières");
        }
    }

    // ------------------------------------------------------------------ utilitaires

    private List<StudentEnrollment> roster(Long classId) {
        return studentEnrollmentRepository.findStudentsWithUserByClassIdAndStatusIn(classId, ROSTER_STATUSES).stream()
                .collect(Collectors.toMap(se -> se.getStudent().getId(), se -> se, (a, b) -> a, LinkedHashMap::new))
                .values().stream()
                .sorted(Comparator.comparing((StudentEnrollment se) -> se.getStudent().getUser().getLastName(),
                                String.CASE_INSENSITIVE_ORDER)
                        .thenComparing(se -> se.getStudent().getUser().getFirstName(), String.CASE_INSENSITIVE_ORDER))
                .toList();
    }

    static boolean isEffective(TeacherScheduleSlot slot, LocalDate today) {
        return !slot.getEffectiveFrom().isAfter(today)
                && (slot.getEffectiveTo() == null || !slot.getEffectiveTo().isBefore(today));
    }

    static String fullName(User user) {
        return (user.getFirstName() + " " + user.getLastName()).trim();
    }
}
