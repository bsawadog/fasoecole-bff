package org.afritechinnovations.service.academic;

import lombok.RequiredArgsConstructor;
import org.afritechinnovations.dto.academic.OwnerGradeDto;
import org.afritechinnovations.model.academic.AcademicYear;
import org.afritechinnovations.model.academic.Attendance;
import org.afritechinnovations.model.academic.AttendanceStatus;
import org.afritechinnovations.model.academic.ClassSubjectTeacher;
import org.afritechinnovations.model.academic.Evaluation;
import org.afritechinnovations.model.academic.Grade;
import org.afritechinnovations.model.academic.GradeHistory;
import org.afritechinnovations.model.academic.GradePeriod;
import org.afritechinnovations.model.academic.GradePeriodStatus;
import org.afritechinnovations.model.academic.ReportCard;
import org.afritechinnovations.model.academic.SchoolClass;
import org.afritechinnovations.model.common.School;
import org.afritechinnovations.model.common.User;
import org.afritechinnovations.model.people.EnrollmentStatus;
import org.afritechinnovations.model.people.Student;
import org.afritechinnovations.model.people.StudentEnrollment;
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
import org.afritechinnovations.model.common.StaffModule;
import org.afritechinnovations.security.SchoolPermissions;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Notes, évaluations et bulletins pilotés par le propriétaire de l'établissement :
 * périodes (ouverture / verrouillage / publication), évaluations, saisie tracée, moyennes pondérées,
 * classements, analyses et bulletins.
 */
@Service
@RequiredArgsConstructor
@Transactional
public class OwnerGradeService {

    static final Set<String> EVALUATION_TYPES =
            Set.of("DEVOIR", "INTERROGATION", "COMPOSITION", "EXAMEN", "ORAL", "TP", "PROJET");
    private final SchoolPermissions permissions;

    private final SchoolRepository schoolRepository;
    private final AcademicYearRepository academicYearRepository;
    private final SchoolClassRepository schoolClassRepository;
    private final ClassSubjectTeacherRepository classSubjectTeacherRepository;
    private final StudentEnrollmentRepository studentEnrollmentRepository;
    private final GradePeriodRepository gradePeriodRepository;
    private final EvaluationRepository evaluationRepository;
    private final GradeRepository gradeRepository;
    private final GradeHistoryRepository gradeHistoryRepository;
    private final ReportCardRepository reportCardRepository;
    private final AttendanceRepository attendanceRepository;
    private final UserRepository userRepository;

    private Clock clock = Clock.systemDefaultZone();

    void setClock(Clock clock) {
        this.clock = clock;
    }

    // ================================================================== périodes

    @Transactional(readOnly = true)
    public List<OwnerGradeDto.PeriodInfo> listPeriods(Long schoolId, Long ownerId, boolean systemAdmin) {
        requireOwnedSchool(schoolId, ownerId, systemAdmin);
        return gradePeriodRepository.findBySchoolIdOrdered(schoolId).stream().map(this::toPeriodInfo).toList();
    }

    public OwnerGradeDto.PeriodInfo createPeriod(Long schoolId, OwnerGradeDto.PeriodRequest request,
                                                 Long ownerId, boolean systemAdmin) {
        School school = requireOwnedSchool(schoolId, ownerId, systemAdmin);
        AcademicYear year = requireYearOfSchool(request.academicYearId(), schoolId);
        String code = normalizeCode(request.code());
        if (gradePeriodRepository.existsBySchoolIdAndAcademicYearIdAndCodeIgnoreCase(schoolId, year.getId(), code)) {
            throw new IllegalArgumentException("Une période avec le code " + code + " existe déjà pour cette année");
        }
        GradePeriod period = GradePeriod.builder().school(school).academicYear(year).code(code).build();
        applyPeriod(period, request, year);
        return toPeriodInfo(gradePeriodRepository.save(period));
    }

    public OwnerGradeDto.PeriodInfo updatePeriod(Long periodId, OwnerGradeDto.PeriodRequest request,
                                                 Long ownerId, boolean systemAdmin) {
        GradePeriod period = requireOwnedPeriod(periodId, ownerId, systemAdmin);
        requireEditable(period);
        if (!Objects.equals(period.getAcademicYear().getId(), request.academicYearId())) {
            throw new IllegalArgumentException("L'année scolaire d'une période ne peut pas être modifiée");
        }
        String code = normalizeCode(request.code());
        if (!code.equals(period.getCode())) {
            if (evaluationRepository.countByPeriodId(periodId) > 0) {
                throw new IllegalArgumentException("Le code ne peut plus changer : des évaluations existent");
            }
            if (gradePeriodRepository.existsBySchoolIdAndAcademicYearIdAndCodeIgnoreCaseAndIdNot(
                    period.getSchool().getId(), period.getAcademicYear().getId(), code, periodId)) {
                throw new IllegalArgumentException("Une période avec le code " + code + " existe déjà pour cette année");
            }
            period.setCode(code);
        }
        applyPeriod(period, request, period.getAcademicYear());
        return toPeriodInfo(period);
    }

    public void deletePeriod(Long periodId, Long ownerId, boolean systemAdmin) {
        GradePeriod period = requireOwnedPeriod(periodId, ownerId, systemAdmin);
        requireEditable(period);
        if (evaluationRepository.countByPeriodId(periodId) > 0) {
            throw new IllegalArgumentException("Supprimez d'abord les évaluations de cette période");
        }
        gradePeriodRepository.delete(period);
    }

    /** Crée en une fois les trimestres (3) ou semestres (2) d'une année, sans toucher aux codes existants. */
    public List<OwnerGradeDto.PeriodInfo> createDefaultPeriods(Long schoolId, OwnerGradeDto.DefaultPeriodsRequest request,
                                                               Long ownerId, boolean systemAdmin) {
        School school = requireOwnedSchool(schoolId, ownerId, systemAdmin);
        AcademicYear year = requireYearOfSchool(request.academicYearId(), schoolId);
        String scheme = request.scheme().trim().toUpperCase(Locale.ROOT);
        int parts;
        String prefix;
        String[] names;
        switch (scheme) {
            case "TRIMESTRE" -> {
                parts = 3;
                prefix = "TERM";
                names = new String[]{"1er trimestre", "2e trimestre", "3e trimestre"};
            }
            case "SEMESTRE" -> {
                parts = 2;
                prefix = "SEM";
                names = new String[]{"1er semestre", "2e semestre"};
            }
            default -> throw new IllegalArgumentException("Découpage inconnu : choisissez TRIMESTRE ou SEMESTRE");
        }
        long totalDays = ChronoUnit.DAYS.between(year.getStartDate(), year.getEndDate()) + 1;
        List<OwnerGradeDto.PeriodInfo> created = new ArrayList<>();
        LocalDate start = year.getStartDate();
        for (int i = 0; i < parts; i++) {
            LocalDate end = i == parts - 1
                    ? year.getEndDate()
                    : year.getStartDate().plusDays(totalDays * (i + 1) / parts - 1);
            String code = prefix + (i + 1);
            if (!gradePeriodRepository.existsBySchoolIdAndAcademicYearIdAndCodeIgnoreCase(schoolId, year.getId(), code)) {
                GradePeriod period = gradePeriodRepository.save(GradePeriod.builder()
                        .school(school).academicYear(year).code(code).name(names[i])
                        .startDate(start).endDate(end).build());
                created.add(toPeriodInfo(period));
            }
            start = end.plusDays(1);
        }
        if (created.isEmpty()) {
            throw new IllegalArgumentException("Ces périodes existent déjà pour l'année " + year.getLabel());
        }
        return created;
    }

    /**
     * OPEN ↔ LOCKED, puis publication : la publication fige les bulletins (recalcul + validation) de toutes les
     * classes de l'année ; la dépublication les repasse en brouillon.
     */
    public OwnerGradeDto.PeriodInfo changeStatus(Long periodId, GradePeriodStatus target, Long ownerId,
                                                 boolean systemAdmin) {
        GradePeriod period = requireOwnedPeriod(periodId, ownerId, systemAdmin);
        GradePeriodStatus current = period.getStatus();
        if (current == target) {
            return toPeriodInfo(period);
        }
        List<SchoolClass> classes = schoolClassRepository.findAllWithLevelBySchoolAndYear(
                period.getSchool().getId(), period.getAcademicYear().getId());
        if (target == GradePeriodStatus.PUBLISHED) {
            for (SchoolClass cls : classes) {
                Computation computation = compute(cls, period);
                saveReportCards(computation, true);
            }
            period.setPublishedAt(LocalDateTime.now(clock));
        } else if (current == GradePeriodStatus.PUBLISHED) {
            for (SchoolClass cls : classes) {
                reportCardRepository.findByPeriodIdAndClassId(periodId, cls.getId())
                        .forEach(card -> card.setValidated(false));
            }
            period.setPublishedAt(null);
        }
        period.setStatus(target);
        return toPeriodInfo(period);
    }

    // ================================================================== matières & coefficients

    @Transactional(readOnly = true)
    public List<OwnerGradeDto.ClassSubjectInfo> listClassSubjects(Long classId, Long ownerId, boolean systemAdmin) {
        requireOwnedClass(classId, ownerId, systemAdmin);
        Map<Long, List<ClassSubjectTeacher>> bySubject = classSubjectTeacherRepository
                .findAllWithTeacherAndSubjectByClassId(classId).stream()
                .collect(Collectors.groupingBy(cst -> cst.getSubject().getId(), LinkedHashMap::new, Collectors.toList()));
        return bySubject.values().stream()
                .map(rows -> new OwnerGradeDto.ClassSubjectInfo(
                        rows.get(0).getSubject().getId(),
                        rows.get(0).getSubject().getName(),
                        coefficientOf(rows),
                        defaultCoefficient(rows),
                        overrideOf(rows).isPresent(),
                        rows.stream()
                                .sorted(Comparator.comparing(ClassSubjectTeacher::isActive).reversed())
                                .map(cst -> new OwnerGradeDto.AssignmentInfo(cst.getId(), cst.getTeacher().getId(),
                                        fullName(cst.getTeacher().getUser()), cst.isActive()))
                                .toList()))
                .sorted(Comparator.comparing(OwnerGradeDto.ClassSubjectInfo::subjectName, String.CASE_INSENSITIVE_ORDER))
                .toList();
    }

    /**
     * Ajuste le coefficient de la matière pour cette classe. {@code null} (ou une valeur égale au
     * coefficient par défaut de la matière) supprime la surcharge : la classe hérite de la matière.
     */
    public List<OwnerGradeDto.ClassSubjectInfo> updateCoefficient(Long classId, Long subjectId, BigDecimal coefficient,
                                                                  Long ownerId, boolean systemAdmin) {
        requireOwnedClass(classId, ownerId, systemAdmin);
        List<ClassSubjectTeacher> rows = classSubjectTeacherRepository.findBySchoolClassId(classId).stream()
                .filter(cst -> cst.getSubject().getId().equals(subjectId))
                .toList();
        if (rows.isEmpty()) {
            throw new IllegalArgumentException("Cette matière n'est pas enseignée dans la classe");
        }
        BigDecimal value = coefficient == null ? null : coefficient.setScale(2, RoundingMode.HALF_UP);
        if (value != null && value.compareTo(defaultCoefficient(rows)) == 0) {
            value = null;
        }
        BigDecimal override = value;
        rows.forEach(cst -> cst.setCoefficient(override));
        return listClassSubjects(classId, ownerId, systemAdmin);
    }

    // ================================================================== évaluations

    @Transactional(readOnly = true)
    public List<OwnerGradeDto.EvaluationInfo> listEvaluations(Long classId, Long periodId, Long ownerId,
                                                              boolean systemAdmin) {
        SchoolClass cls = requireOwnedClass(classId, ownerId, systemAdmin);
        GradePeriod period = requireOwnedPeriod(periodId, ownerId, systemAdmin);
        requireSameYear(cls, period);
        long studentCount = activeRoster(classId).size();
        Map<Long, List<Grade>> gradesByEval = gradeRepository.findForClassAndTerm(classId, period.getCode()).stream()
                .filter(g -> g.getEvaluation() != null)
                .collect(Collectors.groupingBy(g -> g.getEvaluation().getId()));
        return evaluationRepository.findByClassAndPeriod(classId, periodId).stream()
                .map(e -> toEvaluationInfo(e, gradesByEval.getOrDefault(e.getId(), List.of()), studentCount))
                .toList();
    }

    public OwnerGradeDto.EvaluationInfo createEvaluation(Long classId, OwnerGradeDto.EvaluationRequest request,
                                                         Long ownerId, boolean systemAdmin) {
        SchoolClass cls = requireOwnedClass(classId, ownerId, systemAdmin);
        GradePeriod period = requireOwnedPeriod(request.periodId(), ownerId, systemAdmin);
        requireSameYear(cls, period);
        requireEditable(period);
        ClassSubjectTeacher cst = requireAssignmentOfClass(request.classSubjectTeacherId(), classId);
        if (!cst.isActive()) {
            throw new IllegalArgumentException("Cet enseignant est désactivé pour cette matière dans la classe");
        }
        Evaluation evaluation = Evaluation.builder()
                .classSubjectTeacher(cst).period(period).createdAt(LocalDateTime.now(clock)).build();
        applyEvaluation(evaluation, request, period);
        evaluationRepository.save(evaluation);
        return toEvaluationInfo(evaluation, List.of(), activeRoster(classId).size());
    }

    public OwnerGradeDto.EvaluationInfo updateEvaluation(Long evaluationId, OwnerGradeDto.EvaluationRequest request,
                                                         Long ownerId, boolean systemAdmin) {
        Evaluation evaluation = requireOwnedEvaluation(evaluationId, ownerId, systemAdmin);
        GradePeriod period = evaluation.getPeriod();
        requireEditable(period);
        if (!period.getId().equals(request.periodId())) {
            throw new IllegalArgumentException("La période d'une évaluation ne peut pas être modifiée");
        }
        Long classId = evaluation.getClassSubjectTeacher().getSchoolClass().getId();
        List<Grade> grades = gradeRepository.findByEvaluationId(evaluationId);
        if (!evaluation.getClassSubjectTeacher().getId().equals(request.classSubjectTeacherId())) {
            if (!grades.isEmpty()) {
                throw new IllegalArgumentException("La matière ne peut plus changer : des notes sont saisies");
            }
            ClassSubjectTeacher cst = requireAssignmentOfClass(request.classSubjectTeacherId(), classId);
            if (!cst.isActive()) {
                throw new IllegalArgumentException("Cet enseignant est désactivé pour cette matière dans la classe");
            }
            evaluation.setClassSubjectTeacher(cst);
        }
        BigDecimal newMax = request.maxValue() == null ? BigDecimal.valueOf(20) : request.maxValue();
        for (Grade grade : grades) {
            if (grade.getValue().compareTo(newMax) > 0) {
                throw new IllegalArgumentException("Le barème ne peut pas être inférieur à une note déjà saisie ("
                        + grade.getValue().stripTrailingZeros().toPlainString() + ")");
            }
        }
        applyEvaluation(evaluation, request, period);
        for (Grade grade : grades) {
            grade.setMaxValue(evaluation.getMaxValue());
            grade.setType(evaluation.getType());
            grade.setGradeDate(evaluation.getEvalDate());
        }
        return toEvaluationInfo(evaluation, grades, activeRoster(classId).size());
    }

    public void deleteEvaluation(Long evaluationId, String reason, Long actorId, boolean systemAdmin) {
        Evaluation evaluation = requireOwnedEvaluation(evaluationId, actorId, systemAdmin);
        requireEditable(evaluation.getPeriod());
        List<Grade> grades = gradeRepository.findByEvaluationId(evaluationId);
        if (!grades.isEmpty() && isBlank(reason)) {
            throw new IllegalArgumentException("Indiquez le motif de suppression : des notes ont déjà été saisies");
        }
        String actorName = actorName(actorId);
        LocalDateTime now = LocalDateTime.now(clock);
        for (Grade grade : grades) {
            gradeHistoryRepository.save(history(evaluation, grade.getStudent().getId(), "DELETE",
                    grade.getValue(), null, reason, actorId, actorName, now));
        }
        gradeRepository.deleteAll(grades);
        evaluationRepository.delete(evaluation);
    }

    // ================================================================== saisie des notes

    @Transactional(readOnly = true)
    public OwnerGradeDto.GradeSheet gradeSheet(Long evaluationId, Long ownerId, boolean systemAdmin) {
        Evaluation evaluation = requireOwnedEvaluation(evaluationId, ownerId, systemAdmin);
        SchoolClass cls = evaluation.getClassSubjectTeacher().getSchoolClass();
        List<Grade> grades = gradeRepository.findByEvaluationId(evaluationId);
        Map<Long, BigDecimal> values = grades.stream()
                .collect(Collectors.toMap(g -> g.getStudent().getId(), Grade::getValue, (a, b) -> a));
        List<Student> roster = activeRoster(cls.getId());
        List<OwnerGradeDto.SheetRow> rows = roster.stream()
                .map(s -> new OwnerGradeDto.SheetRow(s.getId(), fullName(s.getUser()), s.getRegistrationNumber(),
                        values.get(s.getId())))
                .toList();
        return new OwnerGradeDto.GradeSheet(toEvaluationInfo(evaluation, grades, roster.size()),
                toPeriodInfo(evaluation.getPeriod()), cls.getName(), rows);
    }

    /**
     * Enregistre la feuille de notes. Une note vide supprime la note existante ; toute modification ou suppression
     * d'une note déjà saisie exige un motif et alimente l'historique (ancienne / nouvelle valeur, auteur, date).
     */
    public OwnerGradeDto.SaveGradesResult saveGrades(Long evaluationId, OwnerGradeDto.SaveGradesRequest request,
                                                     Long actorId, boolean systemAdmin) {
        Evaluation evaluation = requireOwnedEvaluation(evaluationId, actorId, systemAdmin);
        requireEditable(evaluation.getPeriod());
        SchoolClass cls = evaluation.getClassSubjectTeacher().getSchoolClass();
        Map<Long, Student> roster = activeRoster(cls.getId()).stream()
                .collect(Collectors.toMap(Student::getId, Function.identity(), (a, b) -> a));
        Map<Long, Grade> existing = gradeRepository.findByEvaluationId(evaluationId).stream()
                .collect(Collectors.toMap(g -> g.getStudent().getId(), Function.identity(), (a, b) -> a));

        List<Change> changes = new ArrayList<>();
        Set<Long> seen = new java.util.HashSet<>();
        for (OwnerGradeDto.GradeEntry entry : request.grades()) {
            if (!seen.add(entry.studentId())) {
                throw new IllegalArgumentException("Un élève apparaît plusieurs fois dans la feuille");
            }
            Grade current = existing.get(entry.studentId());
            if (current == null && !roster.containsKey(entry.studentId())) {
                throw new IllegalArgumentException("L'élève " + entry.studentId() + " n'est pas inscrit dans la classe");
            }
            BigDecimal value = normalizeValue(entry.value(), evaluation.getMaxValue());
            BigDecimal old = current == null ? null : current.getValue();
            if (old == null && value == null) {
                continue;
            }
            if (old != null && value != null && old.compareTo(value) == 0) {
                continue;
            }
            changes.add(new Change(entry.studentId(), current, old, value));
        }
        boolean altersExisting = changes.stream().anyMatch(c -> c.oldValue() != null);
        if (altersExisting && isBlank(request.reason())) {
            throw new IllegalArgumentException("Indiquez le motif de la modification des notes déjà saisies");
        }

        String actorName = actorName(actorId);
        LocalDateTime now = LocalDateTime.now(clock);
        String reason = isBlank(request.reason()) ? null : request.reason().trim();
        int created = 0;
        int updated = 0;
        int deleted = 0;
        for (Change change : changes) {
            String action;
            if (change.grade() == null) {
                gradeRepository.save(Grade.builder()
                        .student(roster.get(change.studentId()))
                        .classSubjectTeacher(evaluation.getClassSubjectTeacher())
                        .evaluation(evaluation)
                        .term(evaluation.getPeriod().getCode())
                        .type(evaluation.getType())
                        .value(change.newValue())
                        .maxValue(evaluation.getMaxValue())
                        .gradeDate(evaluation.getEvalDate())
                        .build());
                action = "CREATE";
                created++;
            } else if (change.newValue() == null) {
                gradeRepository.delete(change.grade());
                action = "DELETE";
                deleted++;
            } else {
                change.grade().setValue(change.newValue());
                action = "UPDATE";
                updated++;
            }
            gradeHistoryRepository.save(history(evaluation, change.studentId(), action, change.oldValue(),
                    change.newValue(), change.oldValue() == null ? null : reason, actorId, actorName, now));
        }
        int unchanged = request.grades().size() - changes.size();
        return new OwnerGradeDto.SaveGradesResult(created, updated, deleted, unchanged);
    }

    @Transactional(readOnly = true)
    public List<OwnerGradeDto.HistoryEntry> history(Long classId, Long periodId, Long ownerId, boolean systemAdmin) {
        requireOwnedClass(classId, ownerId, systemAdmin);
        requireOwnedPeriod(periodId, ownerId, systemAdmin);
        Map<Long, String> names = new HashMap<>();
        for (StudentEnrollment se : studentEnrollmentRepository.findAllWithStudentByClassId(classId)) {
            names.put(se.getStudent().getId(), fullName(se.getStudent().getUser()));
        }
        return gradeHistoryRepository.findTop300ByClassIdAndPeriodIdOrderByChangedAtDescIdDesc(classId, periodId)
                .stream()
                .map(h -> new OwnerGradeDto.HistoryEntry(h.getId(), h.getEvaluationId(), h.getEvaluationTitle(),
                        h.getStudentId(), names.getOrDefault(h.getStudentId(), "Élève #" + h.getStudentId()),
                        h.getAction(), h.getOldValue(), h.getNewValue(), h.getReason(), h.getChangedByName(),
                        h.getChangedAt()))
                .toList();
    }

    // ================================================================== résultats & bulletins

    @Transactional(readOnly = true)
    public OwnerGradeDto.ClassResults classResults(Long classId, Long periodId, Long ownerId, boolean systemAdmin) {
        SchoolClass cls = requireOwnedClass(classId, ownerId, systemAdmin);
        GradePeriod period = requireOwnedPeriod(periodId, ownerId, systemAdmin);
        requireSameYear(cls, period);
        return toClassResults(compute(cls, period));
    }

    /** Fige moyennes, rangs et mentions dans les bulletins de la classe (brouillon tant que non publié). */
    public OwnerGradeDto.ClassResults generateReportCards(Long classId, Long periodId, Long ownerId,
                                                          boolean systemAdmin) {
        SchoolClass cls = requireOwnedClass(classId, ownerId, systemAdmin);
        GradePeriod period = requireOwnedPeriod(periodId, ownerId, systemAdmin);
        requireSameYear(cls, period);
        requireNotPublished(period);
        Computation computation = compute(cls, period);
        saveReportCards(computation, false);
        return toClassResults(computation);
    }

    public OwnerGradeDto.ClassResults saveComment(Long classId, Long periodId, Long studentId, String comment,
                                                  Long ownerId, boolean systemAdmin) {
        SchoolClass cls = requireOwnedClass(classId, ownerId, systemAdmin);
        GradePeriod period = requireOwnedPeriod(periodId, ownerId, systemAdmin);
        requireSameYear(cls, period);
        requireNotPublished(period);
        Computation computation = compute(cls, period);
        Student student = computation.roster().stream().filter(s -> s.getId().equals(studentId)).findFirst()
                .orElseThrow(() -> new IllegalArgumentException("L'élève n'est pas inscrit dans la classe"));
        ReportCard card = reportCardRepository.findByPeriodIdAndStudentId(periodId, studentId)
                .orElseGet(() -> newCard(computation, student));
        card.setComment(isBlank(comment) ? null : comment.trim());
        fillCard(card, computation, studentId);
        reportCardRepository.save(card);
        return toClassResults(computation);
    }

    /**
     * Période de référence de l'établissement, choisie comme sur la page « Notes & bulletins » :
     * période de l'année courante contenant la date du jour, sinon première période ouverte de l'année courante,
     * sinon première période de l'année courante, sinon la plus récente.
     */
    @Transactional(readOnly = true)
    public Optional<GradePeriod> referencePeriod(Long schoolId, LocalDate today) {
        List<GradePeriod> periods = gradePeriodRepository.findBySchoolIdOrdered(schoolId);
        Long currentYear = academicYearRepository.findBySchoolIdAndIsCurrentTrue(schoolId)
                .map(AcademicYear::getId).orElse(null);
        List<GradePeriod> ofYear = periods.stream()
                .filter(p -> p.getAcademicYear().getId().equals(currentYear)).toList();
        return ofYear.stream()
                .filter(p -> !p.getStartDate().isAfter(today) && !p.getEndDate().isBefore(today)).findFirst()
                .or(() -> ofYear.stream().filter(p -> p.getStatus() == GradePeriodStatus.OPEN).findFirst())
                .or(() -> ofYear.stream().findFirst())
                .or(() -> periods.stream().findFirst());
    }

    /**
     * Synthèse affichée sur le tableau de bord : celle de la période de référence si elle contient des notes,
     * sinon celle de la période déjà commencée la plus récente qui en contient.
     */
    @Transactional(readOnly = true)
    public Optional<OwnerGradeDto.SchoolSummary> dashboardSummary(Long schoolId, Long ownerId, LocalDate today) {
        Optional<GradePeriod> reference = referencePeriod(schoolId, today);
        if (reference.isEmpty()) {
            return Optional.empty();
        }
        // L'accès a déjà été contrôlé par le tableau de bord (propriétaire ou module DASHBOARD délégué).
        OwnerGradeDto.SchoolSummary summary = schoolSummary(reference.get().getId(), ownerId, true);
        if (summary.rankedCount() > 0) {
            return Optional.of(summary);
        }
        List<GradePeriod> started = gradePeriodRepository.findBySchoolIdOrdered(schoolId).stream()
                .filter(p -> !p.getId().equals(reference.get().getId()) && !p.getStartDate().isAfter(today))
                .sorted(Comparator.comparing(GradePeriod::getStartDate).reversed())
                .toList();
        for (GradePeriod period : started) {
            OwnerGradeDto.SchoolSummary candidate = schoolSummary(period.getId(), ownerId, false);
            if (candidate.rankedCount() > 0) {
                return Optional.of(candidate);
            }
        }
        return Optional.of(summary);
    }

    @Transactional(readOnly = true)
    public OwnerGradeDto.SchoolSummary schoolSummary(Long periodId, Long ownerId, boolean systemAdmin) {
        GradePeriod period = requireOwnedPeriod(periodId, ownerId, systemAdmin);
        List<SchoolClass> classes = schoolClassRepository.findAllWithLevelBySchoolAndYear(
                period.getSchool().getId(), period.getAcademicYear().getId());
        List<OwnerGradeDto.ClassSummary> summaries = new ArrayList<>();
        List<Double> allAverages = new ArrayList<>();
        int students = 0;
        int passed = 0;
        for (SchoolClass cls : classes) {
            Computation c = compute(cls, period);
            OwnerGradeDto.ClassStats stats = classStats(c);
            int cards = reportCardRepository.findByPeriodIdAndClassId(periodId, cls.getId()).size();
            summaries.add(new OwnerGradeDto.ClassSummary(cls.getId(), cls.getName(),
                    cls.getLevel() == null ? null : cls.getLevel().getName(), stats.studentCount(),
                    stats.rankedCount(), stats.classAverage(), stats.passRate(), cards));
            allAverages.addAll(c.general().values());
            students += stats.studentCount();
            passed += stats.passCount();
        }
        summaries.sort(Comparator.comparing(OwnerGradeDto.ClassSummary::levelName,
                        Comparator.nullsLast(String.CASE_INSENSITIVE_ORDER))
                .thenComparing(OwnerGradeDto.ClassSummary::className, String.CASE_INSENSITIVE_ORDER));
        return new OwnerGradeDto.SchoolSummary(toPeriodInfo(period), summaries, mean(allAverages),
                allAverages.isEmpty() ? null : percent(passed, allAverages.size()), students, allAverages.size());
    }

    @Transactional(readOnly = true)
    public OwnerGradeDto.BulletinBatch bulletins(Long classId, Long periodId, Long studentId, Long ownerId,
                                                 boolean systemAdmin) {
        SchoolClass cls = requireOwnedClass(classId, ownerId, systemAdmin);
        GradePeriod period = requireOwnedPeriod(periodId, ownerId, systemAdmin);
        requireSameYear(cls, period);
        Computation c = compute(cls, period);
        OwnerGradeDto.ClassStats stats = classStats(c);
        Map<Long, SubjectStatsCalc> subjectStats = subjectStatsMap(c);
        Map<Long, ReportCard> cards = reportCardRepository.findByPeriodIdAndClassId(periodId, classId).stream()
                .collect(Collectors.toMap(card -> card.getStudent().getId(), Function.identity(), (a, b) -> a));
        Map<Long, long[]> attendance = new HashMap<>();
        for (Attendance a : attendanceRepository.findByClassBetween(classId, period.getStartDate(), period.getEndDate())) {
            long[] counts = attendance.computeIfAbsent(a.getStudent().getId(), k -> new long[3]);
            if (a.getStatus() == AttendanceStatus.ABSENT || a.getStatus() == AttendanceStatus.EXCUSED) {
                counts[0]++;
                if (a.getStatus() == AttendanceStatus.ABSENT && isBlank(a.getJustification())) {
                    counts[1]++;
                }
            } else if (a.getStatus() == AttendanceStatus.LATE) {
                counts[2]++;
            }
        }

        List<OwnerGradeDto.Bulletin> bulletins = new ArrayList<>();
        for (Student student : c.roster()) {
            if (studentId != null && !student.getId().equals(studentId)) {
                continue;
            }
            Map<Long, Double> averages = c.subjectAverages().getOrDefault(student.getId(), Map.of());
            List<OwnerGradeDto.BulletinLine> lines = new ArrayList<>();
            double totalCoef = 0;
            double totalWeighted = 0;
            for (SubjectMeta subject : c.subjects()) {
                Double avg = averages.get(subject.subjectId());
                SubjectStatsCalc s = subjectStats.get(subject.subjectId());
                BigDecimal weighted = null;
                if (avg != null) {
                    double coef = subject.coefficient().doubleValue();
                    totalCoef += coef;
                    totalWeighted += avg * coef;
                    weighted = round(avg * coef);
                }
                lines.add(new OwnerGradeDto.BulletinLine(subject.name(), subject.teacherNames(),
                        subject.coefficient(), round(avg), weighted, s.average(), s.min(), s.max(),
                        appreciation(avg)));
            }
            Double general = c.general().get(student.getId());
            ReportCard card = cards.get(student.getId());
            long[] counts = attendance.getOrDefault(student.getId(), new long[3]);
            boolean passed = general != null && general >= c.passMark();
            bulletins.add(new OwnerGradeDto.Bulletin(student.getId(), fullName(student.getUser()),
                    student.getRegistrationNumber(), student.getBirthDate(), student.getGender(), lines,
                    round(totalCoef), round(totalWeighted), round(general), c.ranks().get(student.getId()),
                    c.roster().size(), mention(general, c.passMark()),
                    general == null ? "Non classé(e)" : passed ? "Moyenne atteinte" : "Moyenne non atteinte",
                    card == null ? null : card.getComment(), counts[0], counts[1], counts[2],
                    card != null && Boolean.TRUE.equals(card.getValidated())));
        }
        if (studentId != null && bulletins.isEmpty()) {
            throw new IllegalArgumentException("L'élève n'est pas inscrit dans la classe");
        }
        School school = cls.getSchool();
        return new OwnerGradeDto.BulletinBatch(school.getName(), school.getAddress(), school.getPhone(),
                school.getEmail(), period.getAcademicYear().getLabel(), period.getName(), cls.getName(),
                cls.getLevel() == null ? null : cls.getLevel().getName(), period.getPassMark(),
                stats.classAverage(), stats.highest(), stats.lowest(),
                period.getStatus() == GradePeriodStatus.PUBLISHED, bulletins);
    }

    // ================================================================== calcul

    /** Moyennes annuelles d'une classe et seuil de réussite de l'année. */
    public record AnnualResults(double passMark, Map<Long, Double> averages) {
    }

    /**
     * Moyenne annuelle = moyenne des moyennes générales obtenues sur les périodes de l'année de la classe.
     * L'appelant doit avoir contrôlé l'accès à la classe.
     */
    @Transactional(readOnly = true)
    public AnnualResults annualResults(SchoolClass cls) {
        List<GradePeriod> periods = gradePeriodRepository.findBySchoolIdOrdered(cls.getSchool().getId()).stream()
                .filter(p -> p.getAcademicYear().getId().equals(cls.getAcademicYear().getId()))
                .toList();
        double passMark = periods.isEmpty() ? 10.0 : periods.get(periods.size() - 1).getPassMark().doubleValue();
        Map<Long, double[]> sums = new HashMap<>();
        for (GradePeriod period : periods) {
            compute(cls, period).general().forEach((studentId, avg) -> {
                double[] acc = sums.computeIfAbsent(studentId, k -> new double[2]);
                acc[0] += avg;
                acc[1] += 1;
            });
        }
        Map<Long, Double> averages = new HashMap<>();
        sums.forEach((studentId, acc) -> averages.put(studentId, acc[0] / acc[1]));
        return new AnnualResults(passMark, averages);
    }

    private record SubjectMeta(Long subjectId, String name, BigDecimal coefficient, String teacherNames) {
    }

    private record Computation(SchoolClass schoolClass, GradePeriod period, double passMark, List<Student> roster,
                               List<SubjectMeta> subjects, Map<Long, Map<Long, Double>> subjectAverages,
                               Map<Long, Double> general, Map<Long, Integer> ranks) {
    }

    private record Change(Long studentId, Grade grade, BigDecimal oldValue, BigDecimal newValue) {
    }

    private record SubjectStatsCalc(BigDecimal average, BigDecimal min, BigDecimal max, BigDecimal passRate,
                                    int graded) {
    }

    /**
     * Moyenne d'une matière = Σ(note ramenée sur 20 × poids de l'évaluation) / Σ poids.
     * Moyenne générale = Σ(moyenne matière × coefficient) / Σ coefficients des matières notées.
     */
    private Computation compute(SchoolClass cls, GradePeriod period) {
        List<ClassSubjectTeacher> assignments = classSubjectTeacherRepository.findAllWithTeacherAndSubjectByClassId(cls.getId());
        List<Grade> grades = gradeRepository.findForClassAndTerm(cls.getId(), period.getCode());
        List<Student> roster = activeRoster(cls.getId());
        Set<Long> rosterIds = roster.stream().map(Student::getId).collect(Collectors.toSet());

        Set<Long> gradedSubjects = grades.stream()
                .map(g -> g.getClassSubjectTeacher().getSubject().getId()).collect(Collectors.toSet());
        Map<Long, List<ClassSubjectTeacher>> bySubject = assignments.stream()
                .collect(Collectors.groupingBy(cst -> cst.getSubject().getId(), LinkedHashMap::new, Collectors.toList()));
        List<SubjectMeta> subjects = bySubject.values().stream()
                .filter(rows -> rows.stream().anyMatch(ClassSubjectTeacher::isActive)
                        || gradedSubjects.contains(rows.get(0).getSubject().getId()))
                .map(rows -> new SubjectMeta(rows.get(0).getSubject().getId(), rows.get(0).getSubject().getName(),
                        coefficientOf(rows), teacherNames(rows)))
                .sorted(Comparator.comparing(SubjectMeta::name, String.CASE_INSENSITIVE_ORDER))
                .toList();
        Map<Long, BigDecimal> coefficients = subjects.stream()
                .collect(Collectors.toMap(SubjectMeta::subjectId, SubjectMeta::coefficient));

        Map<Long, Map<Long, double[]>> sums = new HashMap<>();
        for (Grade g : grades) {
            Long studentId = g.getStudent().getId();
            if (!rosterIds.contains(studentId) || g.getMaxValue() == null || g.getMaxValue().signum() <= 0) {
                continue;
            }
            Long subjectId = g.getClassSubjectTeacher().getSubject().getId();
            double weight = g.getEvaluation() == null ? 1.0 : g.getEvaluation().getWeight().doubleValue();
            double on20 = g.getValue().doubleValue() / g.getMaxValue().doubleValue() * 20.0;
            double[] acc = sums.computeIfAbsent(studentId, k -> new HashMap<>())
                    .computeIfAbsent(subjectId, k -> new double[2]);
            acc[0] += on20 * weight;
            acc[1] += weight;
        }

        Map<Long, Map<Long, Double>> subjectAverages = new HashMap<>();
        Map<Long, Double> general = new HashMap<>();
        sums.forEach((studentId, bySubj) -> {
            Map<Long, Double> avgs = new HashMap<>();
            double weighted = 0;
            double coefSum = 0;
            for (Map.Entry<Long, double[]> e : bySubj.entrySet()) {
                double avg = e.getValue()[0] / e.getValue()[1];
                avgs.put(e.getKey(), avg);
                double coef = coefficients.getOrDefault(e.getKey(), BigDecimal.ONE).doubleValue();
                weighted += avg * coef;
                coefSum += coef;
            }
            subjectAverages.put(studentId, avgs);
            if (coefSum > 0) {
                general.put(studentId, weighted / coefSum);
            }
        });

        List<Map.Entry<Long, Double>> ordered = general.entrySet().stream()
                .sorted(Map.Entry.<Long, Double>comparingByValue().reversed()).toList();
        Map<Long, Integer> ranks = new HashMap<>();
        BigDecimal previous = null;
        int previousRank = 0;
        for (int i = 0; i < ordered.size(); i++) {
            BigDecimal rounded = round(ordered.get(i).getValue());
            int rank = rounded.equals(previous) ? previousRank : i + 1;
            ranks.put(ordered.get(i).getKey(), rank);
            previous = rounded;
            previousRank = rank;
        }
        return new Computation(cls, period, period.getPassMark().doubleValue(), roster, subjects, subjectAverages,
                general, ranks);
    }

    private OwnerGradeDto.ClassResults toClassResults(Computation c) {
        Map<Long, ReportCard> cards = reportCardRepository.findByPeriodIdAndClassId(c.period().getId(),
                        c.schoolClass().getId()).stream()
                .collect(Collectors.toMap(card -> card.getStudent().getId(), Function.identity(), (a, b) -> a));
        List<OwnerGradeDto.StudentResult> students = c.roster().stream()
                .map(s -> {
                    Map<Long, BigDecimal> avgs = new LinkedHashMap<>();
                    c.subjectAverages().getOrDefault(s.getId(), Map.of()).forEach((k, v) -> avgs.put(k, round(v)));
                    Double general = c.general().get(s.getId());
                    ReportCard card = cards.get(s.getId());
                    return new OwnerGradeDto.StudentResult(s.getId(), fullName(s.getUser()), s.getRegistrationNumber(),
                            avgs, round(general), c.ranks().get(s.getId()), mention(general, c.passMark()),
                            general == null ? null : general >= c.passMark(),
                            card == null ? null : card.getComment(), card != null && card.getGeneratedAt() != null,
                            card != null && Boolean.TRUE.equals(card.getValidated()));
                })
                .sorted(Comparator.comparing(OwnerGradeDto.StudentResult::rank, Comparator.nullsLast(Integer::compare))
                        .thenComparing(OwnerGradeDto.StudentResult::fullName, String.CASE_INSENSITIVE_ORDER))
                .toList();
        Map<Long, SubjectStatsCalc> subjectStats = subjectStatsMap(c);
        List<OwnerGradeDto.SubjectStat> stats = c.subjects().stream()
                .map(s -> {
                    SubjectStatsCalc calc = subjectStats.get(s.subjectId());
                    return new OwnerGradeDto.SubjectStat(s.subjectId(), s.name(), s.coefficient(), calc.average(),
                            calc.min(), calc.max(), calc.passRate(), calc.graded());
                })
                .toList();
        SchoolClass cls = c.schoolClass();
        return new OwnerGradeDto.ClassResults(cls.getId(), cls.getName(),
                cls.getLevel() == null ? null : cls.getLevel().getName(), toPeriodInfo(c.period()),
                c.subjects().stream().map(s -> new OwnerGradeDto.SubjectColumn(s.subjectId(), s.name(), s.coefficient()))
                        .toList(),
                students, classStats(c), stats);
    }

    private OwnerGradeDto.ClassStats classStats(Computation c) {
        List<Double> averages = new ArrayList<>(c.general().values());
        int passCount = (int) averages.stream().filter(a -> a >= c.passMark()).count();
        Map<String, Integer> mentions = new LinkedHashMap<>();
        for (String m : List.of("Très bien", "Bien", "Assez bien", "Passable", "Insuffisant")) {
            mentions.put(m, 0);
        }
        averages.forEach(a -> mentions.merge(mention(a, c.passMark()), 1, Integer::sum));
        return new OwnerGradeDto.ClassStats(c.roster().size(), averages.size(), mean(averages),
                averages.stream().max(Double::compare).map(OwnerGradeService::round).orElse(null),
                averages.stream().min(Double::compare).map(OwnerGradeService::round).orElse(null),
                passCount, averages.isEmpty() ? null : percent(passCount, averages.size()), mentions);
    }

    private Map<Long, SubjectStatsCalc> subjectStatsMap(Computation c) {
        Map<Long, SubjectStatsCalc> result = new HashMap<>();
        for (SubjectMeta subject : c.subjects()) {
            List<Double> values = c.subjectAverages().values().stream()
                    .map(m -> m.get(subject.subjectId())).filter(Objects::nonNull).toList();
            long passed = values.stream().filter(v -> v >= c.passMark()).count();
            result.put(subject.subjectId(), new SubjectStatsCalc(mean(values),
                    values.stream().min(Double::compare).map(OwnerGradeService::round).orElse(null),
                    values.stream().max(Double::compare).map(OwnerGradeService::round).orElse(null),
                    values.isEmpty() ? null : percent(passed, values.size()), values.size()));
        }
        return result;
    }

    private void saveReportCards(Computation c, boolean validate) {
        Map<Long, ReportCard> cards = reportCardRepository.findByPeriodIdAndClassId(c.period().getId(),
                        c.schoolClass().getId()).stream()
                .collect(Collectors.toMap(card -> card.getStudent().getId(), Function.identity(), (a, b) -> a));
        for (Student student : c.roster()) {
            ReportCard card = cards.get(student.getId());
            if (card == null) {
                card = reportCardRepository.findByPeriodIdAndStudentId(c.period().getId(), student.getId())
                        .orElseGet(() -> newCard(c, student));
            }
            fillCard(card, c, student.getId());
            card.setValidated(validate);
            reportCardRepository.save(card);
        }
    }

    private ReportCard newCard(Computation c, Student student) {
        return ReportCard.builder()
                .student(student)
                .academicYear(c.period().getAcademicYear())
                .term(c.period().getCode())
                .periodId(c.period().getId())
                .classId(c.schoolClass().getId())
                .validated(false)
                .build();
    }

    private void fillCard(ReportCard card, Computation c, Long studentId) {
        Double general = c.general().get(studentId);
        card.setClassId(c.schoolClass().getId());
        card.setAverage(round(general));
        card.setRank(c.ranks().get(studentId));
        card.setClassSize(c.roster().size());
        card.setMention(mention(general, c.passMark()));
        card.setGeneratedAt(LocalDateTime.now(clock));
    }

    static String mention(Double average, double passMark) {
        if (average == null) {
            return null;
        }
        if (average < passMark) {
            return "Insuffisant";
        }
        if (average >= 16) {
            return "Très bien";
        }
        if (average >= 14) {
            return "Bien";
        }
        if (average >= 12) {
            return "Assez bien";
        }
        return "Passable";
    }

    static String appreciation(Double average) {
        if (average == null) {
            return "Non évalué";
        }
        if (average >= 16) {
            return "Excellent";
        }
        if (average >= 14) {
            return "Très bien";
        }
        if (average >= 12) {
            return "Bien";
        }
        if (average >= 10) {
            return "Assez bien";
        }
        if (average >= 8) {
            return "Insuffisant";
        }
        return "Faible";
    }

    // ================================================================== contrôles d'accès

    private School requireOwnedSchool(Long schoolId, Long ownerId, boolean systemAdmin) {
        School school = schoolRepository.findById(schoolId)
                .orElseThrow(() -> new IllegalArgumentException("Établissement introuvable : " + schoolId));
        if (!systemAdmin && (school.getOwner() == null || !school.getOwner().getId().equals(ownerId))
                && !permissions.staffAllows(school.getId(), ownerId, StaffModule.GRADES)) {
            throw new AccessDeniedException("Vous ne pouvez gérer que les notes de vos établissements");
        }
        return school;
    }

    private SchoolClass requireOwnedClass(Long classId, Long ownerId, boolean systemAdmin) {
        SchoolClass cls = schoolClassRepository.findById(classId)
                .orElseThrow(() -> new IllegalArgumentException("Classe introuvable : " + classId));
        requireOwnedSchool(cls.getSchool().getId(), ownerId, systemAdmin);
        return cls;
    }

    private GradePeriod requireOwnedPeriod(Long periodId, Long ownerId, boolean systemAdmin) {
        GradePeriod period = gradePeriodRepository.findById(periodId)
                .orElseThrow(() -> new IllegalArgumentException("Période introuvable : " + periodId));
        requireOwnedSchool(period.getSchool().getId(), ownerId, systemAdmin);
        return period;
    }

    private Evaluation requireOwnedEvaluation(Long evaluationId, Long ownerId, boolean systemAdmin) {
        Evaluation evaluation = evaluationRepository.findById(evaluationId)
                .orElseThrow(() -> new IllegalArgumentException("Évaluation introuvable : " + evaluationId));
        requireOwnedSchool(evaluation.getClassSubjectTeacher().getSchoolClass().getSchool().getId(), ownerId,
                systemAdmin);
        return evaluation;
    }

    private ClassSubjectTeacher requireAssignmentOfClass(Long cstId, Long classId) {
        ClassSubjectTeacher cst = classSubjectTeacherRepository.findById(cstId)
                .orElseThrow(() -> new IllegalArgumentException("Affectation introuvable : " + cstId));
        if (!cst.getSchoolClass().getId().equals(classId)) {
            throw new IllegalArgumentException("Cette matière n'appartient pas à la classe");
        }
        return cst;
    }

    private AcademicYear requireYearOfSchool(Long yearId, Long schoolId) {
        AcademicYear year = academicYearRepository.findById(yearId)
                .orElseThrow(() -> new IllegalArgumentException("Année scolaire introuvable : " + yearId));
        if (!year.getSchool().getId().equals(schoolId)) {
            throw new IllegalArgumentException("Cette année scolaire n'appartient pas à l'établissement");
        }
        return year;
    }

    private static void requireSameYear(SchoolClass cls, GradePeriod period) {
        if (!cls.getSchool().getId().equals(period.getSchool().getId())
                || !cls.getAcademicYear().getId().equals(period.getAcademicYear().getId())) {
            throw new IllegalArgumentException("La période ne correspond pas à l'année scolaire de la classe");
        }
    }

    private static void requireEditable(GradePeriod period) {
        if (!period.isEditable()) {
            throw new IllegalArgumentException("La période « " + period.getName() + " » est "
                    + (period.getStatus() == GradePeriodStatus.PUBLISHED ? "publiée" : "verrouillée")
                    + " : déverrouillez-la pour modifier les notes");
        }
    }

    private static void requireNotPublished(GradePeriod period) {
        if (period.getStatus() == GradePeriodStatus.PUBLISHED) {
            throw new IllegalArgumentException("Les bulletins de « " + period.getName()
                    + " » sont publiés : dépubliez la période pour les modifier");
        }
    }

    // ================================================================== utilitaires

    private List<Student> activeRoster(Long classId) {
        // Les élèves d'une année clôturée (COMPLETED) restent sur les résultats et bulletins de leur classe.
        return studentEnrollmentRepository.findStudentsWithUserByClassIdAndStatusIn(classId,
                        List.of(EnrollmentStatus.ACTIVE, EnrollmentStatus.COMPLETED))
                .stream()
                .map(StudentEnrollment::getStudent)
                .collect(Collectors.toMap(Student::getId, Function.identity(), (a, b) -> a, LinkedHashMap::new))
                .values().stream()
                .sorted(Comparator.comparing((Student s) -> s.getUser().getLastName(), String.CASE_INSENSITIVE_ORDER)
                        .thenComparing(s -> s.getUser().getFirstName(), String.CASE_INSENSITIVE_ORDER))
                .toList();
    }

    private void applyPeriod(GradePeriod period, OwnerGradeDto.PeriodRequest request, AcademicYear year) {
        if (request.endDate().isBefore(request.startDate())) {
            throw new IllegalArgumentException("La date de fin doit suivre la date de début");
        }
        if (request.startDate().isBefore(year.getStartDate()) || request.endDate().isAfter(year.getEndDate())) {
            throw new IllegalArgumentException("La période doit être comprise dans l'année scolaire " + year.getLabel());
        }
        period.setName(request.name().trim());
        period.setStartDate(request.startDate());
        period.setEndDate(request.endDate());
        period.setPassMark(request.passMark() == null ? BigDecimal.TEN
                : request.passMark().setScale(2, RoundingMode.HALF_UP));
    }

    private void applyEvaluation(Evaluation evaluation, OwnerGradeDto.EvaluationRequest request, GradePeriod period) {
        String type = request.type().trim().toUpperCase(Locale.ROOT);
        if (!EVALUATION_TYPES.contains(type)) {
            throw new IllegalArgumentException("Type d'évaluation inconnu : " + request.type());
        }
        if (request.evalDate().isBefore(period.getStartDate()) || request.evalDate().isAfter(period.getEndDate())) {
            throw new IllegalArgumentException("La date doit être comprise dans la période (" + period.getStartDate()
                    + " → " + period.getEndDate() + ")");
        }
        evaluation.setTitle(request.title().trim());
        evaluation.setType(type);
        evaluation.setEvalDate(request.evalDate());
        evaluation.setMaxValue((request.maxValue() == null ? BigDecimal.valueOf(20) : request.maxValue())
                .setScale(2, RoundingMode.HALF_UP));
        evaluation.setWeight((request.weight() == null ? BigDecimal.ONE : request.weight())
                .setScale(2, RoundingMode.HALF_UP));
    }

    private static BigDecimal normalizeValue(BigDecimal value, BigDecimal max) {
        if (value == null) {
            return null;
        }
        if (value.signum() < 0 || value.compareTo(max) > 0) {
            throw new IllegalArgumentException("Chaque note doit être comprise entre 0 et "
                    + max.stripTrailingZeros().toPlainString());
        }
        return value.setScale(2, RoundingMode.HALF_UP);
    }

    private OwnerGradeDto.PeriodInfo toPeriodInfo(GradePeriod p) {
        return new OwnerGradeDto.PeriodInfo(p.getId(), p.getSchool().getId(), p.getAcademicYear().getId(),
                p.getAcademicYear().getLabel(), p.getCode(), p.getName(), p.getStartDate(), p.getEndDate(),
                p.getPassMark(), p.getStatus(), p.getPublishedAt(),
                p.getId() == null ? 0 : evaluationRepository.countByPeriodId(p.getId()));
    }

    private OwnerGradeDto.EvaluationInfo toEvaluationInfo(Evaluation e, List<Grade> grades, long studentCount) {
        ClassSubjectTeacher cst = e.getClassSubjectTeacher();
        List<Double> on20 = grades.stream()
                .map(g -> g.getValue().doubleValue() / e.getMaxValue().doubleValue() * 20.0).toList();
        return new OwnerGradeDto.EvaluationInfo(e.getId(), cst.getId(), cst.getSubject().getId(),
                cst.getSubject().getName(), fullName(cst.getTeacher().getUser()), e.getPeriod().getId(), e.getTitle(),
                e.getType(), e.getEvalDate(), e.getMaxValue(), e.getWeight(), grades.size(), studentCount, mean(on20));
    }

    private GradeHistory history(Evaluation evaluation, Long studentId, String action, BigDecimal oldValue,
                                 BigDecimal newValue, String reason, Long actorId, String actorName,
                                 LocalDateTime now) {
        ClassSubjectTeacher cst = evaluation.getClassSubjectTeacher();
        return GradeHistory.builder()
                .evaluationId(evaluation.getId())
                .evaluationTitle(cst.getSubject().getName() + " — " + evaluation.getTitle())
                .classId(cst.getSchoolClass().getId())
                .periodId(evaluation.getPeriod().getId())
                .studentId(studentId)
                .action(action)
                .oldValue(oldValue)
                .newValue(newValue)
                .reason(isBlank(reason) ? null : reason.trim())
                .changedBy(actorId)
                .changedByName(actorName)
                .changedAt(now)
                .build();
    }

    private String actorName(Long actorId) {
        return userRepository.findById(actorId).map(OwnerGradeService::fullName).orElse("Utilisateur #" + actorId);
    }

    private static BigDecimal coefficientOf(List<ClassSubjectTeacher> rows) {
        return overrideOf(rows).orElseGet(() -> defaultCoefficient(rows));
    }

    /** Surcharge définie pour la classe (affectations actives en priorité), si elle existe. */
    private static Optional<BigDecimal> overrideOf(List<ClassSubjectTeacher> rows) {
        List<ClassSubjectTeacher> active = rows.stream().filter(ClassSubjectTeacher::isActive).toList();
        return (active.isEmpty() ? rows : active).stream()
                .map(ClassSubjectTeacher::getCoefficient).filter(Objects::nonNull)
                .max(BigDecimal::compareTo);
    }

    private static BigDecimal defaultCoefficient(List<ClassSubjectTeacher> rows) {
        BigDecimal value = rows.isEmpty() ? null : rows.get(0).getSubject().getCoefficient();
        return value != null ? value : BigDecimal.ONE;
    }

    private static String teacherNames(List<ClassSubjectTeacher> rows) {
        List<ClassSubjectTeacher> active = rows.stream().filter(ClassSubjectTeacher::isActive).toList();
        return (active.isEmpty() ? rows : active).stream()
                .map(cst -> fullName(cst.getTeacher().getUser())).distinct()
                .collect(Collectors.joining(", "));
    }

    private static String normalizeCode(String code) {
        String normalized = code.trim().toUpperCase(Locale.ROOT).replaceAll("\\s+", "");
        if (!normalized.matches("[A-Z0-9_-]{1,20}")) {
            throw new IllegalArgumentException("Le code ne peut contenir que des lettres, chiffres, - ou _");
        }
        return normalized;
    }

    private static String fullName(User user) {
        return (user.getFirstName() + " " + user.getLastName()).trim();
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    static BigDecimal round(Double value) {
        return value == null ? null : BigDecimal.valueOf(value).setScale(2, RoundingMode.HALF_UP);
    }

    private static BigDecimal mean(List<Double> values) {
        return values.isEmpty() ? null
                : round(values.stream().mapToDouble(Double::doubleValue).average().orElse(0));
    }

    private static BigDecimal percent(long part, long total) {
        return BigDecimal.valueOf(100.0 * part / total).setScale(1, RoundingMode.HALF_UP);
    }
}
