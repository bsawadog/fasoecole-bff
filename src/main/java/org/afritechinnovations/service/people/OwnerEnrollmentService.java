package org.afritechinnovations.service.people;

import lombok.RequiredArgsConstructor;
import org.afritechinnovations.dto.people.NewStudentEnrollmentRequest;
import org.afritechinnovations.dto.people.OwnerEnrollmentDto;
import org.afritechinnovations.model.finance.FeeType;
import org.afritechinnovations.repository.finance.FeeTypeRepository;
import org.afritechinnovations.model.academic.AcademicYear;
import org.afritechinnovations.model.academic.ClassSubjectTeacher;
import org.afritechinnovations.model.academic.GradePeriod;
import org.afritechinnovations.model.academic.Level;
import org.afritechinnovations.model.academic.SchoolClass;
import org.afritechinnovations.model.common.School;
import org.afritechinnovations.model.common.StaffModule;
import org.afritechinnovations.model.people.EnrollmentDecision;
import org.afritechinnovations.model.people.EnrollmentStatus;
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
import org.springframework.data.domain.PageRequest;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
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
import java.util.stream.Collectors;

/**
 * Préparation d'une nouvelle année scolaire et passage d'année des élèves (admis, redoublants, sortants).
 * Une inscription traitée passe au statut COMPLETED avec sa décision ; l'élève reçoit une nouvelle
 * inscription ACTIVE dans la classe de l'année suivante.
 */
@Service
@RequiredArgsConstructor
@Transactional
public class OwnerEnrollmentService {

    private final SchoolPermissions permissions;
    private final SchoolRepository schoolRepository;
    private final AcademicYearRepository academicYearRepository;
    private final SchoolClassRepository schoolClassRepository;
    private final LevelRepository levelRepository;
    private final ClassSubjectTeacherRepository classSubjectTeacherRepository;
    private final GradePeriodRepository gradePeriodRepository;
    private final StudentEnrollmentRepository enrollmentRepository;
    private final OwnerGradeService gradeService;
    private final ClassRosterService classRosterService;
    private final FeeTypeRepository feeTypeRepository;
    private final org.afritechinnovations.repository.people.ParentRepository parentRepository;
    private final org.afritechinnovations.repository.people.ParentStudentRepository parentStudentRepository;

    // ================================================================== nouveaux élèves

    /** Classes d'une année avec leur effectif actif, pour choisir la classe d'accueil d'un nouvel élève. */
    @Transactional(readOnly = true)
    public List<OwnerEnrollmentDto.TargetClass> yearClasses(Long schoolId, Long yearId, Long userId, boolean systemAdmin) {
        requireSchool(schoolId, userId, systemAdmin);
        AcademicYear year = requireYear(yearId, schoolId);
        Map<Long, Long> enrolled = enrollmentRepository.findByYearWithStudent(year.getId()).stream()
                .filter(e -> e.getStatus() == EnrollmentStatus.ACTIVE)
                .collect(Collectors.groupingBy(e -> e.getSchoolClass().getId(), Collectors.counting()));
        return schoolClassRepository.findAllWithLevelBySchoolAndYear(schoolId, year.getId()).stream()
                .map(c -> new OwnerEnrollmentDto.TargetClass(c.getId(), c.getName(), c.getLevel().getId(),
                        c.getLevel().getName(), c.getCapacity(), enrolled.getOrDefault(c.getId(), 0L)))
                .toList();
    }

    public OwnerEnrollmentDto.RegistrationResult registerStudent(Long schoolId, NewStudentEnrollmentRequest request,
                                                                Long userId, boolean systemAdmin) {
        requireSchool(schoolId, userId, systemAdmin);
        SchoolClass schoolClass = schoolClassRepository.findById(request.getClassId())
                .orElseThrow(() -> new IllegalArgumentException("Classe introuvable : " + request.getClassId()));
        if (!schoolClass.getSchool().getId().equals(schoolId)) {
            throw new IllegalArgumentException("Cette classe n'appartient pas à l'établissement");
        }
        if (schoolClass.getCapacity() != null
                && enrollmentRepository.findBySchoolClassIdAndStatus(schoolClass.getId(), EnrollmentStatus.ACTIVE).size()
                >= schoolClass.getCapacity()) {
            throw new IllegalArgumentException("La classe " + schoolClass.getName() + " est complète ("
                    + schoolClass.getCapacity() + " places)");
        }
        if (request.getGuardians() != null) {
            Set<Long> scope = guardianScope(schoolClass.getSchool(), userId, systemAdmin);
            for (OwnerEnrollmentDto.Guardian guardian : request.getGuardians()) {
                if (guardian.parentId() != null && !parentRepository.isKnownInSchools(guardian.parentId(), scope)) {
                    throw new AccessDeniedException("Ce parent n'est pas connu de vos établissements");
                }
            }
        }
        return classRosterService.enrollNewStudentWithFees(schoolClass, request);
    }

    /** Recherche d'un parent déjà enregistré (au moins 2 caractères) pour le rattacher à un nouvel élève. */
    @Transactional(readOnly = true)
    public List<OwnerEnrollmentDto.GuardianOption> searchGuardians(Long schoolId, String query, Long userId,
                                                                    boolean systemAdmin) {
        School school = requireSchool(schoolId, userId, systemAdmin);
        String text = query == null ? "" : query.trim().toLowerCase(Locale.ROOT);
        if (text.length() < 2) {
            return List.of();
        }
        Set<Long> scope = guardianScope(school, userId, systemAdmin);
        return parentRepository.searchInSchools(scope, "%" + text + "%", PageRequest.of(0, 15)).stream()
                .map(p -> new OwnerEnrollmentDto.GuardianOption(p.getId(), p.getUser().getFirstName(),
                        p.getUser().getLastName(), p.getUser().getEmail(), p.getUser().getPhone(),
                        parentStudentRepository.findChildrenWithUserByParentId(p.getId()).stream()
                                .filter(ps -> scope.contains(ps.getStudent().getSchool().getId()))
                                .map(ps -> ps.getStudent().getUser() == null ? null
                                        : ps.getStudent().getUser().getFirstName() + " " + ps.getStudent().getUser().getLastName())
                                .filter(Objects::nonNull)
                                .distinct()
                                .toList()))
                .toList();
    }

    /**
     * Établissements dont les parents peuvent être proposés : ceux du propriétaire (un parent peut avoir des enfants
     * dans plusieurs de ses écoles) ; un membre du personnel reste limité à l'établissement où il travaille.
     */
    private Set<Long> guardianScope(School school, Long userId, boolean systemAdmin) {
        Set<Long> ids = new java.util.HashSet<>();
        ids.add(school.getId());
        boolean owner = school.getOwner() != null && school.getOwner().getId().equals(userId);
        if (owner || systemAdmin) {
            Long ownerId = school.getOwner() != null ? school.getOwner().getId() : null;
            if (ownerId != null) {
                schoolRepository.findByOwnerId(ownerId).forEach(s -> ids.add(s.getId()));
            }
        }
        return ids;
    }

    @Transactional(readOnly = true)
    public List<OwnerEnrollmentDto.EnrollmentFee> enrollmentFees(Long schoolId, Long userId, boolean systemAdmin) {
        requireSchool(schoolId, userId, systemAdmin);
        return feeTypeRepository.findWithLevelBySchoolId(schoolId).stream()
                .filter(FeeType::isActive)
                .map(f -> new OwnerEnrollmentDto.EnrollmentFee(f.getId(), f.getName(), f.getAmount(),
                        f.getFrequency().name(), f.getLevel() != null ? f.getLevel().getId() : null))
                .toList();
    }

    // ================================================================== années scolaires

    @Transactional(readOnly = true)
    public OwnerEnrollmentDto.Overview overview(Long schoolId, Long userId, boolean systemAdmin) {
        requireSchool(schoolId, userId, systemAdmin);
        return new OwnerEnrollmentDto.Overview(yearInfos(schoolId));
    }

    public OwnerEnrollmentDto.NewYearResult createYear(Long schoolId, OwnerEnrollmentDto.NewYearRequest request,
                                                       Long userId, boolean systemAdmin) {
        School school = requireSchool(schoolId, userId, systemAdmin);
        String label = request.label().trim();
        if (request.endDate().isBefore(request.startDate()) || request.endDate().isEqual(request.startDate())) {
            throw new IllegalArgumentException("La date de fin doit suivre la date de début");
        }
        List<AcademicYear> existing = academicYearRepository.findBySchoolId(schoolId);
        if (existing.stream().anyMatch(y -> y.getLabel().trim().equalsIgnoreCase(label))) {
            throw new IllegalArgumentException("L'année « " + label + " » existe déjà");
        }
        if (existing.stream().anyMatch(y -> !request.startDate().isAfter(y.getEndDate())
                && !request.endDate().isBefore(y.getStartDate()))) {
            throw new IllegalArgumentException("Les dates chevauchent une année scolaire existante");
        }
        AcademicYear source = null;
        if (request.sourceYearId() != null) {
            source = requireYear(request.sourceYearId(), schoolId);
        } else if (request.copyClasses() || request.copyPeriods()) {
            throw new IllegalArgumentException("Choisissez l'année à reprendre");
        }

        AcademicYear year = academicYearRepository.save(AcademicYear.builder()
                .school(school).label(label)
                .startDate(request.startDate()).endDate(request.endDate())
                .isCurrent(false)
                .build());

        int classesCopied = 0;
        int assignmentsCopied = 0;
        if (source != null && request.copyClasses()) {
            for (SchoolClass cls : schoolClassRepository.findAllWithLevelBySchoolAndYear(schoolId, source.getId())) {
                SchoolClass copy = schoolClassRepository.save(SchoolClass.builder()
                        .school(school).academicYear(year).level(cls.getLevel())
                        .name(cls.getName()).capacity(cls.getCapacity())
                        .build());
                classesCopied++;
                if (request.copyTeachers()) {
                    for (ClassSubjectTeacher cst : classSubjectTeacherRepository.findBySchoolClassId(cls.getId())) {
                        if (!cst.isActive()) {
                            continue;
                        }
                        classSubjectTeacherRepository.save(ClassSubjectTeacher.builder()
                                .schoolClass(copy).subject(cst.getSubject()).teacher(cst.getTeacher())
                                .coefficient(cst.getCoefficient()).active(true)
                                .build());
                        assignmentsCopied++;
                    }
                }
            }
        }

        int periodsCopied = 0;
        if (source != null && request.copyPeriods()) {
            long shift = ChronoUnit.DAYS.between(source.getStartDate(), year.getStartDate());
            AcademicYear sourceYear = source;
            for (GradePeriod p : gradePeriodRepository.findBySchoolIdOrdered(schoolId)) {
                if (!p.getAcademicYear().getId().equals(sourceYear.getId())) {
                    continue;
                }
                gradePeriodRepository.save(GradePeriod.builder()
                        .school(school).academicYear(year).code(p.getCode()).name(p.getName())
                        .startDate(p.getStartDate().plusDays(shift)).endDate(p.getEndDate().plusDays(shift))
                        .passMark(p.getPassMark())
                        .build());
                periodsCopied++;
            }
        }

        if (request.makeCurrent()) {
            makeCurrent(year, existing);
        }
        OwnerEnrollmentDto.YearInfo info = yearInfos(schoolId).stream()
                .filter(y -> y.id().equals(year.getId())).findFirst().orElseThrow();
        return new OwnerEnrollmentDto.NewYearResult(info, classesCopied, assignmentsCopied, periodsCopied);
    }

    public OwnerEnrollmentDto.Overview setCurrent(Long yearId, Long userId, boolean systemAdmin) {
        AcademicYear year = academicYearRepository.findById(yearId)
                .orElseThrow(() -> new IllegalArgumentException("Année scolaire introuvable : " + yearId));
        Long schoolId = year.getSchool().getId();
        requireSchool(schoolId, userId, systemAdmin);
        makeCurrent(year, academicYearRepository.findBySchoolId(schoolId));
        return new OwnerEnrollmentDto.Overview(yearInfos(schoolId));
    }

    // ================================================================== passage d'année

    @Transactional(readOnly = true)
    public OwnerEnrollmentDto.PromotionPlan plan(Long schoolId, Long fromYearId, Long toYearId,
                                                 Long userId, boolean systemAdmin) {
        requireSchool(schoolId, userId, systemAdmin);
        AcademicYear from = requireYear(fromYearId, schoolId);
        AcademicYear to = requireYear(toYearId, schoolId);
        requireDistinctYears(from, to);

        List<Level> levels = orderedLevels(schoolId);
        List<SchoolClass> fromClasses = schoolClassRepository.findAllWithLevelBySchoolAndYear(schoolId, from.getId());
        List<SchoolClass> toClasses = schoolClassRepository.findAllWithLevelBySchoolAndYear(schoolId, to.getId());
        List<StudentEnrollment> fromEnrollments = enrollmentRepository.findByYearWithStudent(from.getId());
        List<StudentEnrollment> toEnrollments = enrollmentRepository.findByYearWithStudent(to.getId());

        Map<Long, Long> enrolledByTargetClass = toEnrollments.stream()
                .filter(e -> e.getStatus() == EnrollmentStatus.ACTIVE)
                .collect(Collectors.groupingBy(e -> e.getSchoolClass().getId(), Collectors.counting()));
        Map<Long, StudentEnrollment> targetByStudent = new HashMap<>();
        toEnrollments.forEach(e -> targetByStudent.putIfAbsent(e.getStudent().getId(), e));

        Map<Long, List<StudentEnrollment>> byClass = fromEnrollments.stream()
                .filter(e -> e.getStatus() == EnrollmentStatus.ACTIVE || e.getStatus() == EnrollmentStatus.COMPLETED)
                .collect(Collectors.groupingBy(e -> e.getSchoolClass().getId()));

        long pending = 0;
        long decided = 0;
        List<OwnerEnrollmentDto.ClassPlan> classPlans = new ArrayList<>();
        for (SchoolClass cls : fromClasses) {
            List<StudentEnrollment> rows = byClass.getOrDefault(cls.getId(), List.of());
            Optional<Level> nextLevel = nextLevel(levels, cls.getLevel());
            OwnerGradeService.AnnualResults results = rows.isEmpty()
                    ? new OwnerGradeService.AnnualResults(10.0, Map.of())
                    : gradeService.annualResults(cls);
            BigDecimal passMark = scale(results.passMark());

            List<OwnerEnrollmentDto.StudentPlan> students = new ArrayList<>();
            for (StudentEnrollment e : rows) {
                Double average = results.averages().get(e.getStudent().getId());
                EnrollmentDecision suggestion = average == null || average >= results.passMark()
                        ? (nextLevel.isPresent() ? EnrollmentDecision.PROMOTED : EnrollmentDecision.GRADUATED)
                        : EnrollmentDecision.REPEATED;
                Long suggestedClass = targetClassFor(cls, suggestion, nextLevel, toClasses)
                        .map(SchoolClass::getId).orElse(null);
                boolean isDecided = e.getStatus() == EnrollmentStatus.COMPLETED;
                StudentEnrollment target = targetByStudent.get(e.getStudent().getId());
                if (isDecided) {
                    decided++;
                } else {
                    pending++;
                }
                students.add(new OwnerEnrollmentDto.StudentPlan(
                        e.getId(), e.getStudent().getId(),
                        e.getStudent().getUser().getFirstName(), e.getStudent().getUser().getLastName(),
                        e.getStudent().getRegistrationNumber(),
                        isDecided && e.getDecisionAverage() != null ? e.getDecisionAverage() : scale(average),
                        passMark, suggestion, suggestedClass,
                        isDecided, e.getDecision(),
                        target != null ? target.getSchoolClass().getId() : null,
                        target != null ? target.getSchoolClass().getName() : null));
            }
            classPlans.add(new OwnerEnrollmentDto.ClassPlan(cls.getId(), cls.getName(), cls.getLevel().getId(),
                    cls.getLevel().getName(), nextLevel.isEmpty(), students));
        }

        List<OwnerEnrollmentDto.TargetClass> targets = toClasses.stream()
                .map(c -> new OwnerEnrollmentDto.TargetClass(c.getId(), c.getName(), c.getLevel().getId(),
                        c.getLevel().getName(), c.getCapacity(), enrolledByTargetClass.getOrDefault(c.getId(), 0L)))
                .toList();
        return new OwnerEnrollmentDto.PromotionPlan(from.getId(), from.getLabel(), to.getId(), to.getLabel(),
                classPlans, targets, pending, decided);
    }

    public OwnerEnrollmentDto.PromotionResult apply(Long schoolId, OwnerEnrollmentDto.PromotionRequest request,
                                                    Long userId, boolean systemAdmin) {
        requireSchool(schoolId, userId, systemAdmin);
        AcademicYear from = requireYear(request.fromYearId(), schoolId);
        AcademicYear to = requireYear(request.toYearId(), schoolId);
        requireDistinctYears(from, to);

        Map<Long, SchoolClass> toClasses = schoolClassRepository
                .findAllWithLevelBySchoolAndYear(schoolId, to.getId()).stream()
                .collect(Collectors.toMap(SchoolClass::getId, c -> c, (a, b) -> a, LinkedHashMap::new));
        Map<Long, Long> enrolled = new HashMap<>();
        enrollmentRepository.findByYearWithStudent(to.getId()).stream()
                .filter(e -> e.getStatus() == EnrollmentStatus.ACTIVE)
                .forEach(e -> enrolled.merge(e.getSchoolClass().getId(), 1L, Long::sum));
        Map<Long, OwnerGradeService.AnnualResults> resultsByClass = new HashMap<>();

        int applied = 0;
        List<OwnerEnrollmentDto.Skipped> skipped = new ArrayList<>();
        Set<Long> seen = new java.util.HashSet<>();
        for (OwnerEnrollmentDto.DecisionItem item : request.decisions()) {
            if (!seen.add(item.enrollmentId())) {
                continue;
            }
            StudentEnrollment e = enrollmentRepository.findById(item.enrollmentId()).orElse(null);
            if (e == null || !e.getAcademicYear().getId().equals(from.getId())
                    || !e.getSchoolClass().getSchool().getId().equals(schoolId)) {
                skipped.add(new OwnerEnrollmentDto.Skipped(item.enrollmentId(), null,
                        "Inscription introuvable pour cette année"));
                continue;
            }
            String name = e.getStudent().getUser().getFirstName() + " " + e.getStudent().getUser().getLastName();
            if (e.getStatus() != EnrollmentStatus.ACTIVE) {
                skipped.add(new OwnerEnrollmentDto.Skipped(e.getId(), name, "Décision déjà enregistrée"));
                continue;
            }
            boolean continues = item.decision() == EnrollmentDecision.PROMOTED
                    || item.decision() == EnrollmentDecision.REPEATED;
            SchoolClass target = null;
            if (continues) {
                target = item.targetClassId() == null ? null : toClasses.get(item.targetClassId());
                if (target == null) {
                    skipped.add(new OwnerEnrollmentDto.Skipped(e.getId(), name,
                            "Choisissez une classe de l'année " + to.getLabel()));
                    continue;
                }
                boolean alreadyEnrolled = enrollmentRepository
                        .findByStudentIdAndAcademicYearId(e.getStudent().getId(), to.getId()).stream()
                        .anyMatch(x -> x.getStatus() == EnrollmentStatus.ACTIVE);
                if (alreadyEnrolled) {
                    skipped.add(new OwnerEnrollmentDto.Skipped(e.getId(), name,
                            "Déjà inscrit(e) pour l'année " + to.getLabel()));
                    continue;
                }
                long count = enrolled.getOrDefault(target.getId(), 0L);
                if (target.getCapacity() != null && count >= target.getCapacity()) {
                    skipped.add(new OwnerEnrollmentDto.Skipped(e.getId(), name,
                            "La classe " + target.getName() + " est complète (" + target.getCapacity() + " places)"));
                    continue;
                }
            }

            SchoolClass fromClass = e.getSchoolClass();
            Double average = resultsByClass
                    .computeIfAbsent(fromClass.getId(), id -> gradeService.annualResults(fromClass))
                    .averages().get(e.getStudent().getId());
            e.setStatus(EnrollmentStatus.COMPLETED);
            e.setDecision(item.decision());
            e.setDecisionAverage(scale(average));
            e.setDecidedAt(LocalDateTime.now());
            enrollmentRepository.save(e);

            if (target != null) {
                enrollmentRepository.save(StudentEnrollment.builder()
                        .student(e.getStudent()).schoolClass(target).academicYear(to)
                        .status(EnrollmentStatus.ACTIVE).enrollmentDate(LocalDate.now())
                        .build());
                enrolled.merge(target.getId(), 1L, Long::sum);
            }
            applied++;
        }
        return new OwnerEnrollmentDto.PromotionResult(applied, skipped);
    }

    /** Annule une décision : supprime la réinscription de l'année suivante et rouvre l'inscription. */
    public void undo(Long enrollmentId, Long userId, boolean systemAdmin) {
        StudentEnrollment e = enrollmentRepository.findById(enrollmentId)
                .orElseThrow(() -> new IllegalArgumentException("Inscription introuvable : " + enrollmentId));
        requireSchool(e.getSchoolClass().getSchool().getId(), userId, systemAdmin);
        if (e.getStatus() != EnrollmentStatus.COMPLETED) {
            throw new IllegalArgumentException("Aucune décision à annuler pour cette inscription");
        }
        LocalDate yearStart = e.getAcademicYear().getStartDate();
        List<StudentEnrollment> later = enrollmentRepository.findByStudentId(e.getStudent().getId()).stream()
                .filter(x -> !x.getId().equals(e.getId()))
                .filter(x -> x.getAcademicYear().getStartDate().isAfter(yearStart))
                .toList();
        if (later.stream().anyMatch(x -> x.getStatus() != EnrollmentStatus.ACTIVE
                && x.getStatus() != EnrollmentStatus.TRANSFERRED)) {
            throw new IllegalArgumentException("L'année suivante de cet élève est déjà clôturée : annulez d'abord sa décision");
        }
        later.forEach(enrollmentRepository::delete);
        e.setStatus(EnrollmentStatus.ACTIVE);
        e.setDecision(null);
        e.setDecisionAverage(null);
        e.setDecidedAt(null);
        enrollmentRepository.save(e);
    }

    // ================================================================== utilitaires

    private List<OwnerEnrollmentDto.YearInfo> yearInfos(Long schoolId) {
        Map<Long, Map<EnrollmentStatus, Long>> counts = new HashMap<>();
        for (Object[] row : enrollmentRepository.countBySchoolGroupedByYearAndStatus(schoolId)) {
            counts.computeIfAbsent((Long) row[0], k -> new HashMap<>())
                    .put((EnrollmentStatus) row[1], ((Number) row[2]).longValue());
        }
        Map<Long, Long> classCounts = schoolClassRepository.findBySchoolId(schoolId).stream()
                .collect(Collectors.groupingBy(c -> c.getAcademicYear().getId(), Collectors.counting()));
        return academicYearRepository.findBySchoolId(schoolId).stream()
                .sorted(Comparator.comparing(AcademicYear::getStartDate).reversed())
                .map(y -> {
                    Map<EnrollmentStatus, Long> c = counts.getOrDefault(y.getId(), Map.of());
                    long active = c.getOrDefault(EnrollmentStatus.ACTIVE, 0L);
                    boolean past = y.getEndDate().isBefore(LocalDate.now());
                    return new OwnerEnrollmentDto.YearInfo(y.getId(), y.getLabel(), y.getStartDate(), y.getEndDate(),
                            Boolean.TRUE.equals(y.getIsCurrent()), classCounts.getOrDefault(y.getId(), 0L).intValue(),
                            active, c.getOrDefault(EnrollmentStatus.COMPLETED, 0L), past ? active : 0L);
                })
                .toList();
    }

    private void makeCurrent(AcademicYear year, List<AcademicYear> all) {
        for (AcademicYear y : all) {
            if (!y.getId().equals(year.getId()) && Boolean.TRUE.equals(y.getIsCurrent())) {
                y.setIsCurrent(false);
                academicYearRepository.save(y);
            }
        }
        year.setIsCurrent(true);
        academicYearRepository.save(year);
    }

    private List<Level> orderedLevels(Long schoolId) {
        return levelRepository.findBySchoolIdOrderByOrderIndexAsc(schoolId).stream()
                .sorted(Comparator.comparing(Level::getOrderIndex).thenComparing(Level::getId))
                .toList();
    }

    static Optional<Level> nextLevel(List<Level> ordered, Level current) {
        for (int i = 0; i < ordered.size(); i++) {
            if (ordered.get(i).getId().equals(current.getId())) {
                return i + 1 < ordered.size() ? Optional.of(ordered.get(i + 1)) : Optional.empty();
            }
        }
        return Optional.empty();
    }

    /** Classe proposée : niveau suivant (admis) ou même niveau (redoublant), en gardant la même section si possible. */
    static Optional<SchoolClass> targetClassFor(SchoolClass from, EnrollmentDecision decision, Optional<Level> next,
                                                List<SchoolClass> candidates) {
        Long levelId;
        if (decision == EnrollmentDecision.PROMOTED && next.isPresent()) {
            levelId = next.get().getId();
        } else if (decision == EnrollmentDecision.REPEATED) {
            levelId = from.getLevel().getId();
        } else {
            return Optional.empty();
        }
        List<SchoolClass> sameLevel = candidates.stream()
                .filter(c -> c.getLevel().getId().equals(levelId))
                .sorted(Comparator.comparing(SchoolClass::getName, String.CASE_INSENSITIVE_ORDER))
                .toList();
        if (sameLevel.isEmpty()) {
            return Optional.empty();
        }
        if (decision == EnrollmentDecision.REPEATED) {
            Optional<SchoolClass> sameName = sameLevel.stream()
                    .filter(c -> c.getName().trim().equalsIgnoreCase(from.getName().trim())).findFirst();
            if (sameName.isPresent()) {
                return sameName;
            }
        }
        String section = section(from.getName(), from.getLevel().getName());
        return sameLevel.stream()
                .filter(c -> Objects.equals(section(c.getName(), c.getLevel().getName()), section))
                .findFirst()
                .or(() -> Optional.of(sameLevel.get(0)));
    }

    /** Partie du nom propre à la section : « CP1-A » (niveau CP1) → « a ». */
    static String section(String className, String levelName) {
        String name = className.trim().toLowerCase(Locale.ROOT);
        String level = levelName == null ? "" : levelName.trim().toLowerCase(Locale.ROOT);
        if (!level.isEmpty() && name.startsWith(level)) {
            name = name.substring(level.length());
        }
        return name.replaceAll("^[\\s\\-_/.]+", "").trim();
    }

    private static BigDecimal scale(Double value) {
        return value == null ? null : BigDecimal.valueOf(value).setScale(2, RoundingMode.HALF_UP);
    }

    private static void requireDistinctYears(AcademicYear from, AcademicYear to) {
        if (from.getId().equals(to.getId())) {
            throw new IllegalArgumentException("Choisissez deux années scolaires différentes");
        }
        if (!to.getStartDate().isAfter(from.getStartDate())) {
            throw new IllegalArgumentException("L'année d'arrivée doit suivre l'année de départ");
        }
    }

    private AcademicYear requireYear(Long yearId, Long schoolId) {
        AcademicYear year = academicYearRepository.findById(yearId)
                .orElseThrow(() -> new IllegalArgumentException("Année scolaire introuvable : " + yearId));
        if (!year.getSchool().getId().equals(schoolId)) {
            throw new IllegalArgumentException("Cette année scolaire n'appartient pas à l'établissement");
        }
        return year;
    }

    private School requireSchool(Long schoolId, Long userId, boolean systemAdmin) {
        School school = schoolRepository.findById(schoolId)
                .orElseThrow(() -> new IllegalArgumentException("Établissement introuvable : " + schoolId));
        if (!systemAdmin && (school.getOwner() == null || !school.getOwner().getId().equals(userId))
                && !permissions.staffAllows(schoolId, userId, StaffModule.ENROLLMENT)) {
            throw new AccessDeniedException("Vous ne pouvez gérer que les inscriptions de vos établissements");
        }
        return school;
    }
}
