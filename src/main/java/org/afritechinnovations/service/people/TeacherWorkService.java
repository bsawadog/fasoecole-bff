package org.afritechinnovations.service.people;

import lombok.RequiredArgsConstructor;
import org.afritechinnovations.dto.people.TeacherExtraRequest;
import org.afritechinnovations.dto.people.TeacherPaymentRequest;
import org.afritechinnovations.dto.people.TeacherRateRequest;
import org.afritechinnovations.dto.people.TeacherSessionRequest;
import org.afritechinnovations.dto.people.TeacherSlotRequest;
import org.afritechinnovations.dto.people.TeacherWorkDto;
import org.afritechinnovations.model.academic.AcademicYear;
import org.afritechinnovations.model.academic.ClassSubjectTeacher;
import org.afritechinnovations.model.academic.SchoolClass;
import org.afritechinnovations.model.common.User;
import org.afritechinnovations.model.people.Teacher;
import org.afritechinnovations.model.people.TeacherExtraHour;
import org.afritechinnovations.model.people.TeacherPayment;
import org.afritechinnovations.model.people.TeacherRate;
import org.afritechinnovations.model.people.TeacherRateType;
import org.afritechinnovations.model.people.TeacherScheduleSlot;
import org.afritechinnovations.model.people.TeacherSessionRecord;
import org.afritechinnovations.model.people.TeacherSessionStatus;
import org.afritechinnovations.repository.academic.ClassSubjectTeacherRepository;
import org.afritechinnovations.repository.academic.SchoolClassRepository;
import org.afritechinnovations.repository.people.TeacherExtraHourRepository;
import org.afritechinnovations.repository.people.TeacherPaymentRepository;
import org.afritechinnovations.repository.people.TeacherRateRepository;
import org.afritechinnovations.repository.people.TeacherRepository;
import org.afritechinnovations.repository.people.TeacherScheduleSlotRepository;
import org.afritechinnovations.repository.people.TeacherSessionRecordRepository;
import org.afritechinnovations.dto.people.AssignClassTeacherRequest;
import org.afritechinnovations.dto.people.CreateClassTeacherRequest;
import org.afritechinnovations.model.academic.Subject;
import org.afritechinnovations.model.common.Role;
import org.afritechinnovations.model.common.SchoolUser;
import org.afritechinnovations.repository.academic.SubjectRepository;
import org.afritechinnovations.repository.common.RoleRepository;
import org.afritechinnovations.model.common.School;
import org.afritechinnovations.repository.common.SchoolRepository;
import org.afritechinnovations.service.common.EmailService;
import org.afritechinnovations.repository.common.SchoolUserRepository;
import org.afritechinnovations.repository.common.UserRepository;
import org.afritechinnovations.model.common.StaffModule;
import org.afritechinnovations.security.SchoolPermissions;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.DayOfWeek;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeSet;

/**
 * Gestion du travail des enseignants par le propriétaire : emploi du temps hebdomadaire,
 * pointage des séances, heures supplémentaires, taux et paie mensuelle (montant dû / versé / restant).
 *
 * Règles de calcul pour un mois M :
 *  - séances prévues = occurrences des créneaux hebdomadaires dans M, bornées par la période d'effet du
 *    créneau et par l'année scolaire de la classe ; une séance non pointée (PENDING) n'est pas rémunérée ;
 *  - taux horaire : taux x (heures présentes + heures supplémentaires) ;
 *  - taux mensuel : taux x (heures présentes + heures supplémentaires) / heures prévues (0 si aucune heure prévue).
 * Invariant : le total versé pour un mois ne dépasse jamais le montant dû de ce mois.
 */
@Service
@RequiredArgsConstructor
@Transactional
public class TeacherWorkService {

    private static final DateTimeFormatter HH_MM = DateTimeFormatter.ofPattern("HH:mm");
    private static final BigDecimal SIXTY = BigDecimal.valueOf(60);
    private final SchoolPermissions permissions;

    private final TeacherRepository teacherRepository;
    private final SchoolClassRepository schoolClassRepository;
    private final ClassSubjectTeacherRepository classSubjectTeacherRepository;
    private final TeacherRateRepository teacherRateRepository;
    private final TeacherScheduleSlotRepository slotRepository;
    private final TeacherSessionRecordRepository sessionRecordRepository;
    private final TeacherExtraHourRepository extraHourRepository;
    private final TeacherPaymentRepository teacherPaymentRepository;
    private final SubjectRepository subjectRepository;
    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final SchoolUserRepository schoolUserRepository;
    private final PasswordEncoder passwordEncoder;
    private final SchoolRepository schoolRepository;
    private final EmailService emailService;

    private Clock clock = Clock.systemDefaultZone();

    void setClock(Clock clock) {
        this.clock = clock;
    }

    // ------------------------------------------------------------------ lecture

    @Transactional(readOnly = true)
    public List<TeacherWorkDto.TeacherInfo> listClassTeachers(Long classId, Long ownerId, boolean systemAdmin) {
        SchoolClass schoolClass = schoolClassRepository.findById(classId)
                .orElseThrow(() -> new IllegalArgumentException("Classe introuvable: " + classId));
        if (!systemAdmin && !schoolClass.getSchool().getOwner().getId().equals(ownerId)
                && !permissions.staffAllows(schoolClass.getSchool().getId(), ownerId, StaffModule.TEACHERS)) {
            throw new AccessDeniedException("Vous ne pouvez consulter que les classes de votre établissement");
        }
        Long schoolId = schoolClass.getSchool().getId();

        Map<Long, Teacher> teachers = new LinkedHashMap<>();
        Map<Long, TreeSet<String>> subjects = new HashMap<>();
        Map<Long, Boolean> activeInClass = new HashMap<>();
        for (ClassSubjectTeacher cst : classSubjectTeacherRepository.findAllWithTeacherAndSubjectByClassId(classId)) {
            Teacher teacher = cst.getTeacher();
            if (!teacher.getSchool().getId().equals(schoolId)) {
                continue;
            }
            teachers.putIfAbsent(teacher.getId(), teacher);
            subjects.computeIfAbsent(teacher.getId(), id -> new TreeSet<>()).add(cst.getSubject().getName());
            activeInClass.merge(teacher.getId(), cst.isActive(), Boolean::logicalOr);
        }
        Map<Long, Long> classCounts = activeClassCounts(schoolId);
        return teachers.values().stream()
                .map(teacher -> toTeacherInfo(teacher, List.copyOf(subjects.get(teacher.getId())),
                        activeInClass.get(teacher.getId()), classCounts.getOrDefault(teacher.getId(), 0L)))
                .sorted(Comparator.comparing((TeacherWorkDto.TeacherInfo t) -> !Boolean.TRUE.equals(t.activeInClass()))
                        .thenComparing(t -> nullSafe(t.lastName()))
                        .thenComparing(t -> nullSafe(t.firstName())))
                .toList();
    }

    /** Tous les enseignants de l'établissement de la classe : un enseignant déjà affecté peut y enseigner une autre matière. */
    @Transactional(readOnly = true)
    public List<TeacherWorkDto.TeacherInfo> listClassCandidates(Long classId, Long ownerId, boolean systemAdmin) {
        SchoolClass schoolClass = requireOwnedClass(classId, ownerId, systemAdmin);
        return schoolTeachers(schoolClass.getSchool().getId());
    }

    @Transactional(readOnly = true)
    public List<TeacherWorkDto.TeacherInfo> listSchoolTeachers(Long schoolId, Long ownerId, boolean systemAdmin) {
        School school = schoolRepository.findById(schoolId)
                .orElseThrow(() -> new IllegalArgumentException("Établissement introuvable : " + schoolId));
        if (!systemAdmin && (school.getOwner() == null || !school.getOwner().getId().equals(ownerId))
                && !permissions.staffAllows(school.getId(), ownerId, StaffModule.TEACHERS)) {
            throw new AccessDeniedException("Vous ne pouvez consulter que les enseignants de votre établissement");
        }
        return schoolTeachers(schoolId);
    }

    private List<TeacherWorkDto.TeacherInfo> schoolTeachers(Long schoolId) {
        Map<Long, Long> classCounts = activeClassCounts(schoolId);
        return teacherRepository.findBySchoolId(schoolId).stream()
                .map(teacher -> toTeacherInfo(teacher, subjectsOf(teacher.getId()), null,
                        classCounts.getOrDefault(teacher.getId(), 0L)))
                .sorted(Comparator.comparing((TeacherWorkDto.TeacherInfo t) -> nullSafe(t.lastName()))
                        .thenComparing(t -> nullSafe(t.firstName())))
                .toList();
    }

    private Map<Long, Long> activeClassCounts(Long schoolId) {
        Map<Long, Long> counts = new HashMap<>();
        for (Object[] row : classSubjectTeacherRepository.countActiveClassesByTeacher(schoolId)) {
            counts.put((Long) row[0], ((Number) row[1]).longValue());
        }
        return counts;
    }

    private long activeClassCount(Teacher teacher) {
        return activeClassCounts(teacher.getSchool().getId()).getOrDefault(teacher.getId(), 0L);
    }

    /**
     * Désactive l'enseignant dans la classe : ses affectations sont conservées (fiche, notes, historique) mais
     * inactives, et ses créneaux en cours dans cette classe sont terminés (les séances passées restent).
     */
    public TeacherWorkDto.TeacherInfo deactivateClassTeacher(Long classId, Long teacherId, Long ownerId,
                                                             boolean systemAdmin) {
        SchoolClass schoolClass = requireOwnedClass(classId, ownerId, systemAdmin);
        Teacher teacher = requireOwnedTeacher(teacherId, ownerId, systemAdmin, true);
        List<ClassSubjectTeacher> assignments = classSubjectTeacherRepository
                .findBySchoolClassIdAndTeacherId(schoolClass.getId(), teacher.getId());
        if (assignments.isEmpty()) {
            throw new IllegalArgumentException("Cet enseignant n'est pas affecté à cette classe");
        }
        if (assignments.stream().noneMatch(ClassSubjectTeacher::isActive)) {
            throw new IllegalArgumentException("Cet enseignant est déjà désactivé dans cette classe");
        }
        LocalDateTime now = LocalDateTime.now(clock);
        for (ClassSubjectTeacher assignment : assignments) {
            if (assignment.isActive()) {
                assignment.setActive(false);
                assignment.setDeactivatedAt(now);
            }
        }
        classSubjectTeacherRepository.saveAll(assignments);
        for (TeacherScheduleSlot slot : slotRepository.findAllWithClassByTeacherId(teacher.getId())) {
            if (slot.getEffectiveTo() == null && slot.getSchoolClass().getId().equals(schoolClass.getId())) {
                endSlot(teacher, slot);
            }
        }
        assertPaymentsCovered(teacher, YearMonth.now(clock));
        return toTeacherInfo(teacher, classSubjectNames(assignments), false, activeClassCount(teacher));
    }

    public TeacherWorkDto.TeacherInfo reactivateClassTeacher(Long classId, Long teacherId, Long ownerId,
                                                             boolean systemAdmin) {
        SchoolClass schoolClass = requireOwnedClass(classId, ownerId, systemAdmin);
        Teacher teacher = requireOwnedTeacher(teacherId, ownerId, systemAdmin, false);
        List<ClassSubjectTeacher> assignments = classSubjectTeacherRepository
                .findBySchoolClassIdAndTeacherId(schoolClass.getId(), teacher.getId());
        if (assignments.isEmpty()) {
            throw new IllegalArgumentException("Cet enseignant n'est pas affecté à cette classe");
        }
        if (assignments.stream().allMatch(ClassSubjectTeacher::isActive)) {
            throw new IllegalArgumentException("Cet enseignant est déjà actif dans cette classe");
        }
        for (ClassSubjectTeacher assignment : assignments) {
            assignment.setActive(true);
            assignment.setDeactivatedAt(null);
        }
        classSubjectTeacherRepository.saveAll(assignments);
        return toTeacherInfo(teacher, classSubjectNames(assignments), true, activeClassCount(teacher));
    }

    private static List<String> classSubjectNames(List<ClassSubjectTeacher> assignments) {
        return assignments.stream().map(cst -> cst.getSubject().getName()).distinct().sorted().toList();
    }

    public TeacherWorkDto.TeacherInfo createClassTeacher(Long classId, CreateClassTeacherRequest request,
                                                         Long ownerId, boolean systemAdmin) {
        SchoolClass schoolClass = requireOwnedClass(classId, ownerId, systemAdmin);
        Subject subject = requireSchoolSubject(request.getSubjectId(), schoolClass);
        String email = request.getEmail().trim().toLowerCase();
        if (userRepository.existsByEmailIgnoreCase(email)) {
            throw new IllegalArgumentException("Un utilisateur existe déjà avec ce courriel : " + email);
        }
        User user = userRepository.save(User.builder()
                .firstName(request.getFirstName().trim())
                .lastName(request.getLastName().trim())
                .email(email)
                .passwordHash(passwordEncoder.encode(request.getPassword()))
                .phone(blankToNull(request.getPhone()))
                .active(true)
                .approved(true)
                .build());
        Role teacherRole = roleRepository.findByName("TEACHER")
                .orElseThrow(() -> new IllegalStateException("Rôle non configuré : TEACHER"));
        schoolUserRepository.save(SchoolUser.builder()
                .user(user)
                .school(schoolClass.getSchool())
                .role(teacherRole)
                .build());
        Teacher teacher = teacherRepository.save(Teacher.builder()
                .user(user)
                .school(schoolClass.getSchool())
                .specialty(blankToNull(request.getSpecialty()))
                .hireDate(request.getHireDate())
                .build());
        classSubjectTeacherRepository.save(ClassSubjectTeacher.builder()
                .schoolClass(schoolClass)
                .subject(subject)
                .teacher(teacher)
                .build());
        return toTeacherInfo(teacher, List.of(subject.getName()), true, 1);
    }

    public TeacherWorkDto.TeacherInfo assignClassTeacher(Long classId, AssignClassTeacherRequest request,
                                                         Long ownerId, boolean systemAdmin) {
        SchoolClass schoolClass = requireOwnedClass(classId, ownerId, systemAdmin);
        Subject subject = requireSchoolSubject(request.getSubjectId(), schoolClass);
        Teacher teacher = teacherRepository.findById(request.getTeacherId())
                .orElseThrow(() -> new IllegalArgumentException("Enseignant introuvable : " + request.getTeacherId()));
        if (!teacher.getSchool().getId().equals(schoolClass.getSchool().getId())) {
            throw new IllegalArgumentException("Cet enseignant n'appartient pas à l'établissement de la classe");
        }
        Optional<ClassSubjectTeacher> existing = classSubjectTeacherRepository
                .findBySchoolClassIdAndSubjectIdAndTeacherId(classId, subject.getId(), teacher.getId());
        if (existing.isPresent() && existing.get().isActive()) {
            throw new IllegalArgumentException("Cet enseignant enseigne déjà cette matière dans cette classe");
        }
        if (existing.isPresent()) {
            existing.get().setActive(true);
            existing.get().setDeactivatedAt(null);
            classSubjectTeacherRepository.save(existing.get());
        } else {
            classSubjectTeacherRepository.save(ClassSubjectTeacher.builder()
                    .schoolClass(schoolClass)
                    .subject(subject)
                    .teacher(teacher)
                    .build());
        }
        return toTeacherInfo(teacher, subjectsOf(teacher.getId()), true, activeClassCount(teacher));
    }

    private SchoolClass requireOwnedClass(Long classId, Long ownerId, boolean systemAdmin) {
        SchoolClass schoolClass = schoolClassRepository.findById(classId)
                .orElseThrow(() -> new IllegalArgumentException("Classe introuvable : " + classId));
        if (!systemAdmin && !schoolClass.getSchool().getOwner().getId().equals(ownerId)
                && !permissions.staffAllows(schoolClass.getSchool().getId(), ownerId, StaffModule.TEACHERS)) {
            throw new AccessDeniedException("Vous ne pouvez gérer que les classes de votre établissement");
        }
        return schoolClass;
    }

    private Subject requireSchoolSubject(Long subjectId, SchoolClass schoolClass) {
        Subject subject = subjectRepository.findById(subjectId)
                .orElseThrow(() -> new IllegalArgumentException("Matière introuvable : " + subjectId));
        if (!subject.getSchool().getId().equals(schoolClass.getSchool().getId())) {
            throw new IllegalArgumentException("Cette matière n'appartient pas à l'établissement de la classe");
        }
        return subject;
    }

    private List<String> subjectsOf(Long teacherId) {
        return classSubjectTeacherRepository.findAllWithSubjectAndClassByTeacherId(teacherId).stream()
                .map(cst -> cst.getSubject().getName())
                .distinct()
                .sorted()
                .toList();
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    /** Envoie à l'enseignant, par courriel, le récapitulatif de sa fiche pour le mois demandé. */
    @Transactional(readOnly = true)
    public void sendTeacherSummary(Long teacherId, String month, Long ownerId, boolean systemAdmin) {
        TeacherWorkDto.TeacherDetail detail = getTeacherDetail(teacherId, month, ownerId, systemAdmin);
        Teacher teacher = teacherRepository.findById(teacherId)
                .orElseThrow(() -> new IllegalArgumentException("Enseignant introuvable : " + teacherId));
        String monthLabel = month == null || month.isBlank() ? YearMonth.now(clock).toString() : parseMonth(month).toString();
        String email = detail.teacher().email();
        if (email == null || email.isBlank()) {
            throw new IllegalArgumentException("Cet enseignant n'a pas d'adresse courriel");
        }
        emailService.sendText(email,
                "FasoÉcole — Votre fiche enseignant (" + monthLabel + ")",
                buildSummary(detail, teacher.getSchool().getName(), monthLabel));
    }

    static String buildSummary(TeacherWorkDto.TeacherDetail detail, String schoolName, String month) {
        TeacherWorkDto.TeacherInfo t = detail.teacher();
        TeacherWorkDto.MonthSummary m = detail.month();
        String[] days = {"Lundi", "Mardi", "Mercredi", "Jeudi", "Vendredi", "Samedi", "Dimanche"};
        StringBuilder sb = new StringBuilder();
        sb.append("Bonjour ").append(t.firstName()).append(" ").append(t.lastName()).append(",\n\n")
                .append("Voici le récapitulatif de votre fiche enseignant à ").append(nullSafe(schoolName))
                .append(" pour le mois ").append(month).append(".\n\n");
        sb.append("INFORMATIONS\n")
                .append("- Spécialité : ").append(t.specialty() == null ? "Non renseignée" : t.specialty()).append('\n')
                .append("- Matières : ").append(t.subjects().isEmpty() ? "—" : String.join(", ", t.subjects())).append('\n')
                .append("- Classes actives : ").append(t.classCount()).append("\n\n");
        sb.append("RÉMUNÉRATION\n")
                .append("- Mode : ").append("HOURLY".equals(detail.rateType()) ? "Horaire"
                        : "MONTHLY".equals(detail.rateType()) ? "Mensuel" : "Non défini").append('\n')
                .append("- Taux : ").append(detail.rate() == null ? "Non défini" : fcfa(detail.rate())).append("\n\n");
        sb.append("BILAN DU MOIS\n")
                .append("- Heures prévues : ").append(hours(m.plannedHours())).append('\n')
                .append("- Heures présentes : ").append(hours(m.workedHours())).append('\n')
                .append("- Heures d'absence : ").append(hours(m.absenceHours())).append('\n')
                .append("- Heures supplémentaires : ").append(hours(m.extraHours())).append('\n')
                .append("- Montant dû : ").append(fcfa(m.amountDue())).append('\n')
                .append("- Déjà versé : ").append(fcfa(m.paid())).append('\n')
                .append("- Reste à payer : ").append(fcfa(m.remaining())).append("\n\n");
        List<TeacherWorkDto.SlotInfo> current = detail.schedule().stream().filter(s -> s.effectiveTo() == null).toList();
        sb.append("EMPLOI DU TEMPS EN COURS\n");
        if (current.isEmpty()) {
            sb.append("- Aucun créneau en cours\n");
        }
        for (TeacherWorkDto.SlotInfo s : current) {
            sb.append("- ").append(days[s.dayOfWeek() - 1]).append(' ').append(s.startTime(), 0, 5).append('-')
                    .append(s.endTime(), 0, 5).append(" : ").append(s.className())
                    .append(" (depuis le ").append(s.effectiveFrom()).append(")\n");
        }
        sb.append('\n');
        if (!m.sessions().isEmpty()) {
            sb.append("SÉANCES DU MOIS\n");
            for (TeacherWorkDto.SessionInfo s : m.sessions()) {
                String status = "PRESENT".equals(s.status()) ? "Présent" : "ABSENT".equals(s.status()) ? "Absent" : "À pointer";
                sb.append("- ").append(s.date()).append(' ').append(s.startTime(), 0, 5).append('-').append(s.endTime(), 0, 5)
                        .append(" · ").append(s.className()).append(" · ").append(hours(s.hours())).append(" · ")
                        .append(status).append('\n');
            }
            sb.append('\n');
        }
        if (!m.extras().isEmpty()) {
            sb.append("HEURES SUPPLÉMENTAIRES\n");
            for (TeacherWorkDto.ExtraInfo e : m.extras()) {
                sb.append("- ").append(e.date()).append(" · ").append(e.className()).append(" · ").append(hours(e.hours()))
                        .append(e.description() == null || e.description().isBlank() ? "" : " · " + e.description()).append('\n');
            }
            sb.append('\n');
        }
        if (!m.payments().isEmpty()) {
            sb.append("VERSEMENTS\n");
            for (TeacherWorkDto.PaymentInfo p : m.payments()) {
                sb.append("- ").append(p.date()).append(" · ").append(fcfa(p.amount()))
                        .append(p.reference() == null || p.reference().isBlank() ? "" : " · réf. " + p.reference()).append('\n');
            }
            sb.append('\n');
        }
        sb.append("Pour toute question, contactez l'administration de votre établissement.\n\nL'équipe FasoÉcole");
        return sb.toString();
    }

    private static String hours(BigDecimal value) {
        return (value == null ? BigDecimal.ZERO : value).stripTrailingZeros().toPlainString() + " h";
    }

    private static String fcfa(BigDecimal value) {
        return String.format(java.util.Locale.FRANCE, "%,.0f FCFA", value == null ? BigDecimal.ZERO : value)
                .replace('\u202f', ' ').replace('\u00a0', ' ');
    }

    @Transactional(readOnly = true)
    public TeacherWorkDto.TeacherDetail getTeacherDetail(Long teacherId, String month, Long ownerId,
                                                         boolean systemAdmin) {
        Teacher teacher = requireOwnedTeacher(teacherId, ownerId, systemAdmin, false);
        YearMonth yearMonth = month == null || month.isBlank() ? YearMonth.now(clock) : parseMonth(month);

        List<String> subjects = classSubjectTeacherRepository.findAllWithSubjectAndClassByTeacherId(teacherId)
                .stream()
                .map(cst -> cst.getSubject().getName())
                .distinct()
                .sorted()
                .toList();
        List<TeacherScheduleSlot> slots = slotRepository.findAllWithClassByTeacherId(teacherId);
        MonthComputation computation = computeMonth(teacher, yearMonth, slots);

        return new TeacherWorkDto.TeacherDetail(
                toTeacherInfo(teacher, subjects, null, activeClassCount(teacher)),
                computation.rate().map(rate -> rate.getRateType().name()).orElse(null),
                computation.rate().map(TeacherRate::getAmount).orElse(null),
                slots.stream().map(this::toSlotInfo).toList(),
                computation.summary()
        );
    }

    // ------------------------------------------------------------------ taux

    public TeacherWorkDto.RateInfo addRate(Long teacherId, TeacherRateRequest request, Long ownerId,
                                           boolean systemAdmin) {
        Teacher teacher = requireOwnedTeacher(teacherId, ownerId, systemAdmin, true);
        TeacherRateType type = parseRateType(request.getType());
        requirePositiveAmount(request.getAmount());
        LocalDate effectiveMonth = requireDate(request.getEffectiveFrom(), "La date d'effet").withDayOfMonth(1);

        // Les mois déjà (partiellement) payés ne sont jamais recalculés silencieusement.
        if (teacherPaymentRepository.existsByTeacherIdAndPayMonthGreaterThanEqual(teacherId, effectiveMonth)) {
            throw new IllegalArgumentException("Des paiements sont déjà enregistrés pour " + YearMonth.from(effectiveMonth)
                    + " ou un mois ultérieur : le nouveau taux doit prendre effet après le dernier mois payé");
        }

        TeacherRate rate = teacherRateRepository.findByTeacherIdAndEffectiveFrom(teacherId, effectiveMonth)
                .orElseGet(() -> TeacherRate.builder().teacher(teacher).effectiveFrom(effectiveMonth).build());
        rate.setRateType(type);
        rate.setAmount(request.getAmount().setScale(2, RoundingMode.UNNECESSARY));
        rate = teacherRateRepository.save(rate);
        return new TeacherWorkDto.RateInfo(rate.getId(), rate.getRateType().name(), rate.getAmount(),
                rate.getEffectiveFrom());
    }

    // ------------------------------------------------------------------ emploi du temps

    public TeacherWorkDto.SlotInfo addSlot(Long teacherId, TeacherSlotRequest request, Long ownerId,
                                           boolean systemAdmin) {
        Teacher teacher = requireOwnedTeacher(teacherId, ownerId, systemAdmin, true);
        SchoolClass schoolClass = requireTeacherClass(teacher, request.getClassId());
        Integer dayOfWeek = request.getDayOfWeek();
        if (dayOfWeek == null || dayOfWeek < 1 || dayOfWeek > 7) {
            throw new IllegalArgumentException("Le jour doit être compris entre 1 (lundi) et 7 (dimanche)");
        }
        LocalTime start = parseTime(request.getStartTime(), "L'heure de début");
        LocalTime end = parseTime(request.getEndTime(), "L'heure de fin");
        if (!end.isAfter(start)) {
            throw new IllegalArgumentException("L'heure de fin doit être postérieure à l'heure de début");
        }
        LocalDate effectiveFrom = requireDate(request.getEffectiveFrom(), "La date d'effet");
        AcademicYear academicYear = schoolClass.getAcademicYear();
        if (effectiveFrom.isAfter(academicYear.getEndDate())) {
            throw new IllegalArgumentException("La date d'effet est postérieure à la fin de l'année scolaire de la classe");
        }

        for (TeacherScheduleSlot existing : slotRepository.findAllWithClassByTeacherId(teacherId)) {
            boolean sameDay = existing.getDayOfWeek().equals(dayOfWeek);
            boolean datesOverlap = existing.getEffectiveTo() == null
                    || !existing.getEffectiveTo().isBefore(effectiveFrom);
            boolean timesOverlap = start.isBefore(existing.getEndTime()) && existing.getStartTime().isBefore(end);
            if (sameDay && datesOverlap && timesOverlap) {
                throw new IllegalArgumentException("Ce créneau chevauche un créneau existant de l'enseignant ("
                        + existing.getSchoolClass().getName() + " " + existing.getStartTime().format(HH_MM) + "-"
                        + existing.getEndTime().format(HH_MM) + ")");
            }
        }

        TeacherScheduleSlot slot = slotRepository.save(TeacherScheduleSlot.builder()
                .teacher(teacher)
                .schoolClass(schoolClass)
                .dayOfWeek(dayOfWeek)
                .startTime(start)
                .endTime(end)
                .effectiveFrom(effectiveFrom)
                .build());
        assertPaymentsCovered(teacher, YearMonth.from(effectiveFrom));
        return toSlotInfo(slot);
    }

    /**
     * Archive un créneau à partir d'aujourd'hui : l'historique (séances passées et pointées) est conservé.
     * Un créneau qui n'a encore produit aucune séance passée est simplement supprimé.
     */
    public void archiveSlot(Long teacherId, Long slotId, Long ownerId, boolean systemAdmin) {
        Teacher teacher = requireOwnedTeacher(teacherId, ownerId, systemAdmin, true);
        TeacherScheduleSlot slot = requireTeacherSlot(teacher, slotId);
        if (slot.getEffectiveTo() != null) {
            throw new IllegalArgumentException("Ce créneau est déjà archivé");
        }
        endSlot(teacher, slot);
        assertPaymentsCovered(teacher, YearMonth.from(LocalDate.now(clock)));
    }

    private void endSlot(Teacher teacher, TeacherScheduleSlot slot) {
        LocalDate today = LocalDate.now(clock);
        LocalDate effectiveTo = today.minusDays(1);
        Optional<TeacherSessionRecord> lastRecord = sessionRecordRepository.findFirstBySlotIdOrderBySessionDateDesc(slot.getId());
        if (lastRecord.isPresent() && lastRecord.get().getSessionDate().isAfter(effectiveTo)) {
            effectiveTo = lastRecord.get().getSessionDate();
        }
        if (effectiveTo.isBefore(slot.getEffectiveFrom())) {
            slotRepository.delete(slot);
        } else {
            slot.setEffectiveTo(effectiveTo);
            slotRepository.save(slot);
        }
    }
    // ------------------------------------------------------------------ pointage

    public TeacherWorkDto.SessionInfo recordSession(Long teacherId, TeacherSessionRequest request, Long ownerId,
                                                    boolean systemAdmin) {
        Teacher teacher = requireOwnedTeacher(teacherId, ownerId, systemAdmin, true);
        TeacherScheduleSlot slot = requireTeacherSlot(teacher, request.getSlotId());
        LocalDate date = requireDate(request.getDate(), "La date");
        if (date.isAfter(LocalDate.now(clock))) {
            throw new IllegalArgumentException("Impossible de pointer une séance future");
        }
        if (date.getDayOfWeek().getValue() != slot.getDayOfWeek()) {
            throw new IllegalArgumentException("Cette date ne correspond pas au jour du créneau");
        }
        if (date.isBefore(slot.getEffectiveFrom())
                || (slot.getEffectiveTo() != null && date.isAfter(slot.getEffectiveTo()))) {
            throw new IllegalArgumentException("Cette date est hors de la période d'effet du créneau");
        }
        AcademicYear academicYear = slot.getSchoolClass().getAcademicYear();
        if (date.isBefore(academicYear.getStartDate()) || date.isAfter(academicYear.getEndDate())) {
            throw new IllegalArgumentException("Cette date est hors de l'année scolaire de la classe");
        }
        String status = request.getStatus();
        if (status == null || !List.of("PRESENT", "ABSENT", "PENDING").contains(status)) {
            throw new IllegalArgumentException("Le statut doit être PRESENT, ABSENT ou PENDING");
        }

        Optional<TeacherSessionRecord> existing = sessionRecordRepository.findBySlotIdAndSessionDate(slot.getId(), date);
        if ("PENDING".equals(status)) {
            existing.ifPresent(sessionRecordRepository::delete);
        } else {
            TeacherSessionRecord record = existing.orElseGet(() -> TeacherSessionRecord.builder()
                    .slot(slot).sessionDate(date).build());
            record.setStatus(TeacherSessionStatus.valueOf(status));
            record.setRecordedAt(LocalDateTime.now(clock));
            sessionRecordRepository.save(record);
        }
        assertPaymentsCovered(teacher, YearMonth.from(date));
        return new TeacherWorkDto.SessionInfo(slot.getId(), date, slot.getSchoolClass().getId(),
                slot.getSchoolClass().getName(), slot.getStartTime().format(HH_MM), slot.getEndTime().format(HH_MM),
                status, hoursOf(slotMinutes(slot)));
    }

    // ------------------------------------------------------------------ heures supplémentaires

    public TeacherWorkDto.ExtraInfo addExtra(Long teacherId, TeacherExtraRequest request, Long ownerId,
                                             boolean systemAdmin) {
        Teacher teacher = requireOwnedTeacher(teacherId, ownerId, systemAdmin, true);
        SchoolClass schoolClass = requireTeacherClass(teacher, request.getClassId());
        LocalDate date = requireDate(request.getDate(), "La date");
        if (date.isAfter(LocalDate.now(clock))) {
            throw new IllegalArgumentException("Impossible d'enregistrer des heures supplémentaires futures");
        }
        AcademicYear academicYear = schoolClass.getAcademicYear();
        if (date.isBefore(academicYear.getStartDate()) || date.isAfter(academicYear.getEndDate())) {
            throw new IllegalArgumentException("Cette date est hors de l'année scolaire de la classe");
        }
        BigDecimal hours = request.getHours();
        if (hours == null || hours.signum() <= 0 || hours.compareTo(BigDecimal.valueOf(24)) > 0
                || hours.stripTrailingZeros().scale() > 2) {
            throw new IllegalArgumentException("Le nombre d'heures doit être compris entre 0,01 et 24 (2 décimales maximum)");
        }
        String description = request.getDescription() == null || request.getDescription().isBlank()
                ? null : request.getDescription().trim();
        if (description != null && description.length() > 255) {
            throw new IllegalArgumentException("La description ne peut pas dépasser 255 caractères");
        }

        TeacherExtraHour extra = extraHourRepository.save(TeacherExtraHour.builder()
                .teacher(teacher)
                .schoolClass(schoolClass)
                .workDate(date)
                .hours(hours.setScale(2, RoundingMode.UNNECESSARY))
                .description(description)
                .build());
        assertPaymentsCovered(teacher, YearMonth.from(date));
        return toExtraInfo(extra);
    }

    public void deleteExtra(Long teacherId, Long extraId, Long ownerId, boolean systemAdmin) {
        Teacher teacher = requireOwnedTeacher(teacherId, ownerId, systemAdmin, true);
        TeacherExtraHour extra = extraHourRepository.findById(extraId)
                .orElseThrow(() -> new IllegalArgumentException("Heure supplémentaire introuvable: " + extraId));
        if (!extra.getTeacher().getId().equals(teacher.getId())) {
            throw new IllegalArgumentException("Heure supplémentaire introuvable pour cet enseignant: " + extraId);
        }
        extraHourRepository.delete(extra);
        assertPaymentsCovered(teacher, YearMonth.from(extra.getWorkDate()));
    }

    // ------------------------------------------------------------------ paiements

    public TeacherWorkDto.PaymentInfo addPayment(Long teacherId, TeacherPaymentRequest request, Long ownerId,
                                                 boolean systemAdmin) {
        Teacher teacher = requireOwnedTeacher(teacherId, ownerId, systemAdmin, true);
        YearMonth month = parseMonth(request.getMonth());
        LocalDate date = requireDate(request.getDate(), "La date de paiement");
        if (date.isAfter(LocalDate.now(clock))) {
            throw new IllegalArgumentException("La date de paiement ne peut pas être dans le futur");
        }
        requirePositiveAmount(request.getAmount());

        MonthComputation computation = computeMonth(teacher, month, slotRepository.findAllWithClassByTeacherId(teacherId));
        if (computation.rate().isEmpty()) {
            throw new IllegalArgumentException("Aucun taux n'est défini pour cet enseignant sur " + month);
        }
        if (computation.rate().get().getRateType() == TeacherRateType.MONTHLY && computation.plannedMinutes() == 0) {
            throw new IllegalArgumentException("Aucune heure n'est planifiée sur " + month
                    + " : le prorata du salaire mensuel ne peut pas être calculé");
        }
        BigDecimal remaining = computation.summary().remaining();
        if (request.getAmount().compareTo(remaining) > 0) {
            throw new IllegalArgumentException("Le montant (" + request.getAmount().toPlainString()
                    + ") dépasse le reste à payer pour " + month + " (" + remaining.toPlainString() + ")");
        }

        String reference = request.getReference() == null || request.getReference().isBlank()
                ? null : request.getReference().trim();
        if (reference != null && reference.length() > 100) {
            throw new IllegalArgumentException("La référence ne peut pas dépasser 100 caractères");
        }
        TeacherPayment payment = teacherPaymentRepository.save(TeacherPayment.builder()
                .teacher(teacher)
                .payMonth(month.atDay(1))
                .paymentDate(date)
                .amount(request.getAmount().setScale(2, RoundingMode.UNNECESSARY))
                .reference(reference)
                .build());
        return toPaymentInfo(payment);
    }

    public void deletePayment(Long teacherId, Long paymentId, Long ownerId, boolean systemAdmin) {
        Teacher teacher = requireOwnedTeacher(teacherId, ownerId, systemAdmin, true);
        TeacherPayment payment = teacherPaymentRepository.findById(paymentId)
                .orElseThrow(() -> new IllegalArgumentException("Paiement introuvable: " + paymentId));
        if (!payment.getTeacher().getId().equals(teacher.getId())) {
            throw new IllegalArgumentException("Paiement introuvable pour cet enseignant: " + paymentId);
        }
        teacherPaymentRepository.delete(payment);
    }
    // ------------------------------------------------------------------ calcul mensuel

    record MonthComputation(Optional<TeacherRate> rate, long plannedMinutes, TeacherWorkDto.MonthSummary summary) {
    }

    MonthComputation computeMonth(Teacher teacher, YearMonth month, List<TeacherScheduleSlot> slots) {
        LocalDate first = month.atDay(1);
        LocalDate last = month.atEndOfMonth();
        Optional<TeacherRate> rate = teacherRateRepository
                .findFirstByTeacherIdAndEffectiveFromLessThanEqualOrderByEffectiveFromDesc(teacher.getId(), first);

        Map<String, TeacherSessionStatus> records = new HashMap<>();
        for (TeacherSessionRecord record : sessionRecordRepository.findByTeacherIdAndDateBetween(teacher.getId(), first, last)) {
            records.put(record.getSlot().getId() + "|" + record.getSessionDate(), record.getStatus());
        }

        Map<Long, ClassAccumulator> perClass = new LinkedHashMap<>();
        List<TeacherWorkDto.SessionInfo> sessions = new ArrayList<>();
        long planned = 0;
        long worked = 0;
        long absent = 0;

        for (TeacherScheduleSlot slot : slots) {
            SchoolClass schoolClass = slot.getSchoolClass();
            AcademicYear academicYear = schoolClass.getAcademicYear();
            LocalDate from = max(first, slot.getEffectiveFrom(), academicYear.getStartDate());
            LocalDate to = min(last, slot.getEffectiveTo() != null ? slot.getEffectiveTo() : last,
                    academicYear.getEndDate());
            if (from.isAfter(to)) {
                continue;
            }
            long minutes = slotMinutes(slot);
            ClassAccumulator acc = accumulator(perClass, schoolClass);
            for (LocalDate date = from.with(TemporalAdjusters.nextOrSame(DayOfWeek.of(slot.getDayOfWeek())));
                 !date.isAfter(to); date = date.plusWeeks(1)) {
                TeacherSessionStatus status = records.get(slot.getId() + "|" + date);
                planned += minutes;
                acc.plannedMinutes += minutes;
                if (status == TeacherSessionStatus.PRESENT) {
                    worked += minutes;
                    acc.workedMinutes += minutes;
                } else if (status == TeacherSessionStatus.ABSENT) {
                    absent += minutes;
                    acc.absentMinutes += minutes;
                }
                sessions.add(new TeacherWorkDto.SessionInfo(slot.getId(), date, schoolClass.getId(),
                        schoolClass.getName(), slot.getStartTime().format(HH_MM), slot.getEndTime().format(HH_MM),
                        status == null ? "PENDING" : status.name(), hoursOf(minutes)));
            }
        }
        sessions.sort(Comparator.comparing(TeacherWorkDto.SessionInfo::date)
                .thenComparing(TeacherWorkDto.SessionInfo::startTime));

        List<TeacherExtraHour> extras = extraHourRepository.findByTeacherIdAndDateBetween(teacher.getId(), first, last);
        BigDecimal extraHours = BigDecimal.ZERO.setScale(2);
        for (TeacherExtraHour extra : extras) {
            extraHours = extraHours.add(extra.getHours());
            ClassAccumulator acc = accumulator(perClass, extra.getSchoolClass());
            acc.extraHours = acc.extraHours.add(extra.getHours());
        }

        BigDecimal amountDue = computeAmountDue(rate, planned, worked, extraHours);
        List<TeacherPayment> payments = teacherPaymentRepository
                .findByTeacherIdAndPayMonthOrderByPaymentDateAscIdAsc(teacher.getId(), first);
        BigDecimal paid = payments.stream().map(TeacherPayment::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add).setScale(2, RoundingMode.HALF_UP);

        TeacherWorkDto.MonthSummary summary = new TeacherWorkDto.MonthSummary(
                hoursOf(planned), hoursOf(worked), hoursOf(absent), extraHours,
                amountDue, paid, amountDue.subtract(paid),
                perClass.values().stream()
                        .sorted(Comparator.comparing(acc -> nullSafe(acc.className)))
                        .map(ClassAccumulator::toDto)
                        .toList(),
                sessions,
                extras.stream().map(this::toExtraInfo).toList(),
                payments.stream().map(this::toPaymentInfo).toList()
        );
        return new MonthComputation(rate, planned, summary);
    }

    static BigDecimal computeAmountDue(Optional<TeacherRate> rate, long plannedMinutes, long workedMinutes,
                                       BigDecimal extraHours) {
        if (rate.isEmpty()) {
            return BigDecimal.ZERO.setScale(2);
        }
        BigDecimal amount = rate.get().getAmount();
        // Minutes rémunérées = minutes de présence + heures supplémentaires converties en minutes.
        BigDecimal payableMinutes = BigDecimal.valueOf(workedMinutes).add(extraHours.multiply(SIXTY));
        if (rate.get().getRateType() == TeacherRateType.HOURLY) {
            return amount.multiply(payableMinutes).divide(SIXTY, 2, RoundingMode.HALF_UP);
        }
        if (plannedMinutes == 0) {
            return BigDecimal.ZERO.setScale(2);
        }
        return amount.multiply(payableMinutes).divide(BigDecimal.valueOf(plannedMinutes), 2, RoundingMode.HALF_UP);
    }

    /** Refuse toute modification qui ferait passer un mois déjà payé en sur-paiement. */
    private void assertPaymentsCovered(Teacher teacher, YearMonth fromMonth) {
        List<LocalDate> paidMonths = teacherPaymentRepository
                .findByTeacherIdAndPayMonthGreaterThanEqual(teacher.getId(), fromMonth.atDay(1))
                .stream()
                .map(TeacherPayment::getPayMonth)
                .distinct()
                .sorted()
                .toList();
        if (paidMonths.isEmpty()) {
            return;
        }
        List<TeacherScheduleSlot> slots = slotRepository.findAllWithClassByTeacherId(teacher.getId());
        for (LocalDate paidMonth : paidMonths) {
            TeacherWorkDto.MonthSummary summary = computeMonth(teacher, YearMonth.from(paidMonth), slots).summary();
            if (summary.paid().compareTo(summary.amountDue()) > 0) {
                throw new IllegalArgumentException("Modification refusée : le montant dû pour " + YearMonth.from(paidMonth)
                        + " (" + summary.amountDue().toPlainString() + ") deviendrait inférieur au total déjà versé ("
                        + summary.paid().toPlainString() + "). Supprimez ou ajustez d'abord les paiements de ce mois.");
            }
        }
    }

    private static ClassAccumulator accumulator(Map<Long, ClassAccumulator> perClass, SchoolClass schoolClass) {
        return perClass.computeIfAbsent(schoolClass.getId(),
                id -> new ClassAccumulator(schoolClass.getId(), schoolClass.getName()));
    }

    private static final class ClassAccumulator {
        private final Long classId;
        private final String className;
        private long plannedMinutes;
        private long workedMinutes;
        private long absentMinutes;
        private BigDecimal extraHours = BigDecimal.ZERO.setScale(2);

        private ClassAccumulator(Long classId, String className) {
            this.classId = classId;
            this.className = className;
        }

        private TeacherWorkDto.ClassBreakdown toDto() {
            return new TeacherWorkDto.ClassBreakdown(classId, className, hoursOf(plannedMinutes),
                    hoursOf(workedMinutes), hoursOf(absentMinutes), extraHours);
        }
    }

    // ------------------------------------------------------------------ contrôles d'accès et helpers

    private Teacher requireOwnedTeacher(Long teacherId, Long ownerId, boolean systemAdmin, boolean lock) {
        Optional<Teacher> found = lock ? teacherRepository.findByIdForUpdate(teacherId) : teacherRepository.findById(teacherId);
        Teacher teacher = found.orElseThrow(() -> new IllegalArgumentException("Enseignant introuvable: " + teacherId));
        if (!systemAdmin && !teacher.getSchool().getOwner().getId().equals(ownerId)
                && !permissions.staffAllows(teacher.getSchool().getId(), ownerId, StaffModule.TEACHERS)) {
            throw new AccessDeniedException("Vous ne pouvez gérer que les enseignants de votre établissement");
        }
        return teacher;
    }

    private SchoolClass requireTeacherClass(Teacher teacher, Long classId) {
        if (classId == null) {
            throw new IllegalArgumentException("La classe est obligatoire");
        }
        SchoolClass schoolClass = schoolClassRepository.findById(classId)
                .orElseThrow(() -> new IllegalArgumentException("Classe introuvable: " + classId));
        if (!schoolClass.getSchool().getId().equals(teacher.getSchool().getId())) {
            throw new AccessDeniedException("Cette classe n'appartient pas à l'établissement de l'enseignant");
        }
        if (!classSubjectTeacherRepository.existsBySchoolClassIdAndTeacherId(classId, teacher.getId())) {
            throw new IllegalArgumentException("L'enseignant n'est pas affecté à cette classe");
        }
        if (!classSubjectTeacherRepository.existsBySchoolClassIdAndTeacherIdAndActiveTrue(classId, teacher.getId())) {
            throw new IllegalArgumentException("L'enseignant est désactivé dans cette classe : réactivez-le d'abord");
        }
        return schoolClass;
    }

    private TeacherScheduleSlot requireTeacherSlot(Teacher teacher, Long slotId) {
        if (slotId == null) {
            throw new IllegalArgumentException("Le créneau est obligatoire");
        }
        TeacherScheduleSlot slot = slotRepository.findById(slotId)
                .orElseThrow(() -> new IllegalArgumentException("Créneau introuvable: " + slotId));
        if (!slot.getTeacher().getId().equals(teacher.getId())) {
            throw new IllegalArgumentException("Créneau introuvable pour cet enseignant: " + slotId);
        }
        return slot;
    }

    private static YearMonth parseMonth(String month) {
        if (month == null || !month.matches("^\\d{4}-(0[1-9]|1[0-2])$")) {
            throw new IllegalArgumentException("Mois invalide (format attendu YYYY-MM)");
        }
        return YearMonth.parse(month);
    }

    private static LocalTime parseTime(String value, String label) {
        if (value == null || !value.matches(TeacherSlotRequest.TIME_PATTERN)) {
            throw new IllegalArgumentException(label + " est invalide (format attendu HH:mm)");
        }
        try {
            return LocalTime.parse(value, HH_MM);
        } catch (DateTimeParseException ex) {
            throw new IllegalArgumentException(label + " est invalide (format attendu HH:mm)");
        }
    }

    private static TeacherRateType parseRateType(String type) {
        if ("HOURLY".equals(type)) {
            return TeacherRateType.HOURLY;
        }
        if ("MONTHLY".equals(type)) {
            return TeacherRateType.MONTHLY;
        }
        throw new IllegalArgumentException("Le type de taux doit être HOURLY ou MONTHLY");
    }

    private static void requirePositiveAmount(BigDecimal amount) {
        if (amount == null || amount.signum() <= 0) {
            throw new IllegalArgumentException("Le montant doit être supérieur à zéro");
        }
        if (amount.stripTrailingZeros().scale() > 2) {
            throw new IllegalArgumentException("Le montant ne peut pas avoir plus de 2 décimales");
        }
    }

    private static LocalDate requireDate(LocalDate date, String label) {
        if (date == null) {
            throw new IllegalArgumentException(label + " est obligatoire");
        }
        return date;
    }

    private static long slotMinutes(TeacherScheduleSlot slot) {
        return Duration.between(slot.getStartTime(), slot.getEndTime()).toMinutes();
    }

    private static BigDecimal hoursOf(long minutes) {
        return BigDecimal.valueOf(minutes).divide(SIXTY, 2, RoundingMode.HALF_UP);
    }

    private static LocalDate max(LocalDate a, LocalDate b, LocalDate c) {
        LocalDate result = a.isAfter(b) ? a : b;
        return result.isAfter(c) ? result : c;
    }

    private static LocalDate min(LocalDate a, LocalDate b, LocalDate c) {
        LocalDate result = a.isBefore(b) ? a : b;
        return result.isBefore(c) ? result : c;
    }

    private static String nullSafe(String value) {
        return value == null ? "" : value.toLowerCase();
    }

    private TeacherWorkDto.TeacherInfo toTeacherInfo(Teacher teacher, List<String> subjects, Boolean activeInClass,
                                                     long classCount) {
        User user = teacher.getUser();
        return new TeacherWorkDto.TeacherInfo(teacher.getId(), teacher.getSchool().getId(), user.getFirstName(), user.getLastName(),
                user.getEmail(), user.getPhone(), teacher.getSpecialty(), subjects, activeInClass, classCount);
    }

    private TeacherWorkDto.SlotInfo toSlotInfo(TeacherScheduleSlot slot) {
        return new TeacherWorkDto.SlotInfo(slot.getId(), slot.getSchoolClass().getId(), slot.getSchoolClass().getName(),
                slot.getDayOfWeek(), slot.getStartTime().format(HH_MM), slot.getEndTime().format(HH_MM),
                slot.getEffectiveFrom(), slot.getEffectiveTo());
    }

    private TeacherWorkDto.ExtraInfo toExtraInfo(TeacherExtraHour extra) {
        return new TeacherWorkDto.ExtraInfo(extra.getId(), extra.getSchoolClass().getId(),
                extra.getSchoolClass().getName(), extra.getWorkDate(), extra.getHours(), extra.getDescription());
    }

    private TeacherWorkDto.PaymentInfo toPaymentInfo(TeacherPayment payment) {
        return new TeacherWorkDto.PaymentInfo(payment.getId(), payment.getPaymentDate(), payment.getAmount(),
                payment.getReference());
    }
}
