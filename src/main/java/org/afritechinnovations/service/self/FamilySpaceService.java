package org.afritechinnovations.service.self;

import lombok.RequiredArgsConstructor;
import org.afritechinnovations.dto.academic.OwnerGradeDto;
import org.afritechinnovations.dto.self.SelfServiceDto;
import org.afritechinnovations.model.academic.AcademicYear;
import org.afritechinnovations.model.academic.Attendance;
import org.afritechinnovations.model.academic.AttendanceStatus;
import org.afritechinnovations.model.academic.ClassSubjectTeacher;
import org.afritechinnovations.model.academic.Grade;
import org.afritechinnovations.model.academic.GradePeriod;
import org.afritechinnovations.model.academic.GradePeriodStatus;
import org.afritechinnovations.model.academic.SchoolClass;
import org.afritechinnovations.model.finance.Invoice;
import org.afritechinnovations.model.finance.InvoiceStatus;
import org.afritechinnovations.model.finance.Payment;
import org.afritechinnovations.model.people.EnrollmentStatus;
import org.afritechinnovations.model.people.ParentStudent;
import org.afritechinnovations.model.people.Student;
import org.afritechinnovations.model.people.StudentEnrollment;
import org.afritechinnovations.model.people.TeacherScheduleSlot;
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
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;

/**
 * Espaces élève et parent (lecture seule) : un élève voit sa propre fiche, un parent celles de ses enfants
 * inscrits dans un établissement auquel son accès est toujours actif (accès révocable par le propriétaire).
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class FamilySpaceService {

    private final StudentRepository studentRepository;
    private final ParentRepository parentRepository;
    private final ParentStudentRepository parentStudentRepository;
    private final SchoolUserRepository schoolUserRepository;
    private final StudentEnrollmentRepository enrollmentRepository;
    private final AttendanceRepository attendanceRepository;
    private final InvoiceRepository invoiceRepository;
    private final PaymentRepository paymentRepository;
    private final GradePeriodRepository gradePeriodRepository;
    private final GradeRepository gradeRepository;
    private final TeacherScheduleSlotRepository scheduleSlotRepository;
    private final ClassSubjectTeacherRepository classSubjectTeacherRepository;
    private final OwnerGradeService gradeService;

    private record Accessible(Student student, String relationship) {
    }

    // ------------------------------------------------------------------ fiches

    public List<SelfServiceDto.StudentOverview> students(Long userId) {
        return accessible(userId).values().stream()
                .filter(a -> a.student().getSchool().getStatus() == org.afritechinnovations.model.common.SchoolStatus.ACTIVE)
                .filter(a -> org.afritechinnovations.service.academic.SelectedAcademicYear.schoolMatches(a.student().getSchool().getId()))
                .map(a -> overview(a.student(), a.relationship()))
                .sorted(Comparator.comparing(SelfServiceDto.StudentOverview::fullName, String.CASE_INSENSITIVE_ORDER))
                .toList();
    }

    public SelfServiceDto.StudentOverview student(Long userId, Long studentId) {
        Accessible a = requireAccess(userId, studentId);
        return overview(a.student(), a.relationship());
    }

    /** Fiche complète : identité, responsables légaux, enseignants de la classe et coordonnées de l'école. */
    public SelfServiceDto.StudentProfile profile(Long userId, Long studentId) {
        Accessible a = requireAccess(userId, studentId);
        Student student = a.student();
        SelfServiceDto.StudentOverview overview = overview(student, a.relationship());

        List<SelfServiceDto.GuardianContact> guardians = parentStudentRepository
                .findByStudentIdWithParentUser(student.getId()).stream()
                .map(link -> {
                    var user = link.getParent().getUser();
                    return new SelfServiceDto.GuardianContact(TeacherSpaceService.fullName(user),
                            link.getRelationship(), user.getPhone(), user.getEmail());
                })
                .sorted(Comparator.comparing(SelfServiceDto.GuardianContact::fullName, String.CASE_INSENSITIVE_ORDER))
                .toList();

        List<SelfServiceDto.ClassTeacher> teachers = List.of();
        if (overview.classId() != null) {
            Map<String, Set<String>> subjectsByTeacher = new LinkedHashMap<>();
            for (ClassSubjectTeacher cst : classSubjectTeacherRepository
                    .findAllWithTeacherAndSubjectByClassId(overview.classId())) {
                if (cst.isActive()) {
                    subjectsByTeacher.computeIfAbsent(TeacherSpaceService.fullName(cst.getTeacher().getUser()),
                            k -> new TreeSet<>()).add(cst.getSubject().getName());
                }
            }
            teachers = subjectsByTeacher.entrySet().stream()
                    .map(e -> new SelfServiceDto.ClassTeacher(e.getKey(), String.join(", ", e.getValue())))
                    .sorted(Comparator.comparing(SelfServiceDto.ClassTeacher::fullName, String.CASE_INSENSITIVE_ORDER))
                    .toList();
        }

        var school = student.getSchool();
        var user = student.getUser();
        return new SelfServiceDto.StudentProfile(overview, user == null ? null : user.getEmail(),
                user == null ? null : user.getPhone(), guardians, teachers, schoolContact(school));
    }

    /** Enfant dont l'utilisateur est le parent / tuteur (un élève ne peut pas agir pour lui-même ici). */
    public Student requireGuardedChild(Long userId, Long studentId) {
        Accessible a = requireAccess(userId, studentId);
        boolean linkedParent = parentRepository.findByUserId(userId)
                .map(parent -> parentStudentRepository.findByParentId(parent.getId()).stream()
                        .anyMatch(link -> link.getStudent().getId().equals(studentId))).orElse(false);
        if (!linkedParent) {
            throw new AccessDeniedException("Seul un parent ou tuteur peut effectuer cette action");
        }
        return a.student();
    }

    /** Établissements où l'utilisateur dispose d'un accès parent actif. */
    public List<SelfServiceDto.SchoolContact> parentSchools(Long userId) {
        return schoolUserRepository.findByUserId(userId).stream()
                .filter(su -> su.getRole() != null && "PARENT".equals(su.getRole().getName()))
                .map(su -> su.getSchool())
                .collect(Collectors.toMap(s -> s.getId(), s -> s, (x, y) -> x, LinkedHashMap::new))
                .values().stream()
                .map(FamilySpaceService::schoolContact)
                .sorted(Comparator.comparing(SelfServiceDto.SchoolContact::name, String.CASE_INSENSITIVE_ORDER))
                .toList();
    }

    public static SelfServiceDto.SchoolContact schoolContact(org.afritechinnovations.model.common.School school) {
        return new SelfServiceDto.SchoolContact(school.getId(), school.getName(), school.getAddress(),
                school.getPhone(), school.getEmail());
    }

    // ------------------------------------------------------------------ notes

    public SelfServiceDto.StudentGrades grades(Long userId, Long studentId) {
        Student student = requireAccess(userId, studentId).student();
        Optional<StudentEnrollment> enrollment = currentEnrollment(student.getId());
        if (enrollment.isEmpty()) {
            return new SelfServiceDto.StudentGrades(student.getId(), fullName(student), null, null, List.of());
        }
        SchoolClass cls = enrollment.get().getSchoolClass();
        AcademicYear year = cls.getAcademicYear();
        List<GradePeriod> periods = gradePeriodRepository.findBySchoolIdOrdered(cls.getSchool().getId()).stream()
                .filter(p -> p.getAcademicYear().getId().equals(year.getId()))
                .sorted(Comparator.comparing(GradePeriod::getStartDate))
                .toList();
        Map<String, List<Grade>> gradesByTerm = gradeRepository.findByStudentIdOrderByGradeDateAsc(student.getId())
                .stream()
                .filter(g -> g.getClassSubjectTeacher().getSchoolClass().getAcademicYear().getId().equals(year.getId()))
                .collect(Collectors.groupingBy(g -> g.getEvaluation() != null
                        ? "#" + g.getEvaluation().getPeriod().getId() : g.getTerm()));

        List<SelfServiceDto.PeriodGrades> result = new ArrayList<>();
        for (GradePeriod period : periods) {
            List<Grade> grades = new ArrayList<>(gradesByTerm.getOrDefault("#" + period.getId(), List.of()));
            grades.addAll(gradesByTerm.getOrDefault(period.getCode(), List.of()));
            List<SelfServiceDto.GradeItem> items = grades.stream()
                    .map(g -> new SelfServiceDto.GradeItem(g.getId(), g.getClassSubjectTeacher().getSubject().getName(),
                            g.getEvaluation() == null ? null : g.getEvaluation().getTitle(), g.getType(),
                            g.getGradeDate(), g.getValue(), g.getMaxValue(), g.getAppreciation()))
                    .sorted(Comparator.comparing(SelfServiceDto.GradeItem::date).reversed())
                    .toList();
            boolean published = period.getStatus() == GradePeriodStatus.PUBLISHED;
            OwnerGradeDto.Bulletin bulletin = null;
            BigDecimal classAverage = null;
            if (published) {
                try {
                    OwnerGradeDto.BulletinBatch batch = gradeService.bulletins(cls.getId(), period.getId(),
                            student.getId(), userId, true);
                    bulletin = batch.bulletins().isEmpty() ? null : batch.bulletins().get(0);
                    classAverage = batch.classAverage();
                } catch (IllegalArgumentException ignored) {
                    // l'élève n'apparaît pas (ou plus) dans le classement de cette période
                }
            }
            result.add(new SelfServiceDto.PeriodGrades(period.getId(), period.getName(), period.getStatus(),
                    period.getStartDate(), period.getEndDate(), published, items, bulletin, classAverage));
        }
        return new SelfServiceDto.StudentGrades(student.getId(), fullName(student), cls.getName(), year.getLabel(),
                result);
    }

    // ------------------------------------------------------------------ emploi du temps

    public List<SelfServiceDto.ScheduleEntry> schedule(Long userId, Long studentId) {
        Student student = requireAccess(userId, studentId).student();
        Optional<StudentEnrollment> enrollment = currentEnrollment(student.getId());
        if (enrollment.isEmpty()) {
            return List.of();
        }
        SchoolClass cls = enrollment.get().getSchoolClass();
        Map<Long, String> subjectsByTeacher = classSubjectTeacherRepository
                .findAllWithTeacherAndSubjectByClassId(cls.getId()).stream()
                .filter(ClassSubjectTeacher::isActive)
                .collect(Collectors.groupingBy(cst -> cst.getTeacher().getId(),
                        Collectors.mapping(cst -> cst.getSubject().getName(),
                                Collectors.collectingAndThen(Collectors.toCollection(TreeSet::new),
                                        set -> String.join(", ", set)))));
        LocalDate today = LocalDate.now();
        List<SelfServiceDto.ScheduleEntry> entries = new ArrayList<>();
        for (TeacherScheduleSlot slot : scheduleSlotRepository.findAllWithTeacherByClassId(cls.getId())) {
            if (!TeacherSpaceService.isEffective(slot, org.afritechinnovations.service.academic.SelectedAcademicYear.viewDate(student.getSchool().getId(), today))) {
                continue;
            }
            entries.add(new SelfServiceDto.ScheduleEntry(slot.getId(), slot.getDayOfWeek(), slot.getStartTime(),
                    slot.getEndTime(), cls.getId(), cls.getName(), cls.getSchool().getName(),
                    subjectsByTeacher.get(slot.getTeacher().getId()),
                    TeacherSpaceService.fullName(slot.getTeacher().getUser())));
        }
        return entries;
    }

    // ------------------------------------------------------------------ absences et frais

    public List<SelfServiceDto.AttendanceItem> attendance(Long userId, Long studentId) {
        Student student = requireAccess(userId, studentId).student();
        return attendanceRepository.findByStudentIdOrderByAttendanceDateDesc(student.getId()).stream()
                .filter(a -> org.afritechinnovations.service.academic.SelectedAcademicYear.matches(a.getSchoolClass().getAcademicYear()))
                .filter(a -> a.getStatus() != AttendanceStatus.PRESENT)
                .map(a -> new SelfServiceDto.AttendanceItem(a.getId(), a.getAttendanceDate(), a.getStatus(),
                        a.getJustification()))
                .toList();
    }

    public List<SelfServiceDto.InvoiceItem> invoices(Long userId, Long studentId) {
        Student student = requireAccess(userId, studentId).student();
        return invoiceRepository.findByStudentId(student.getId()).stream()
                .filter(i -> invoiceInView(i))
                .filter(i -> i.getStatus() != InvoiceStatus.CANCELLED)
                .sorted(Comparator.comparing(Invoice::getDueDate))
                .map(i -> {
                    BigDecimal paid = paid(i);
                    BigDecimal net = i.getAmountDue().subtract(nz(i.getDiscountAmount()));
                    return new SelfServiceDto.InvoiceItem(i.getId(), i.getFeeType().getName(), i.getDueDate(),
                            i.getAmountDue(), nz(i.getDiscountAmount()), paid, net.subtract(paid).max(BigDecimal.ZERO),
                            i.getStatus());
                })
                .toList();
    }

    // ------------------------------------------------------------------ accès

    private Map<Long, Accessible> accessible(Long userId) {
        Map<Long, Accessible> result = new LinkedHashMap<>();
        for (Student own : studentRepository.findAllByUserId(userId)) {
            result.put(own.getId(), new Accessible(own, null));
        }
        Set<Long> parentSchools = schoolUserRepository.findByUserId(userId).stream()
                .filter(su -> su.getRole() != null && "PARENT".equals(su.getRole().getName()))
                .map(su -> su.getSchool().getId())
                .collect(Collectors.toSet());
        parentRepository.findByUserId(userId).ifPresent(parent -> {
            for (ParentStudent link : parentStudentRepository.findChildrenWithUserByParentId(parent.getId())) {
                Student child = link.getStudent();
                if (parentSchools.contains(child.getSchool().getId())) {
                    result.putIfAbsent(child.getId(), new Accessible(child, link.getRelationship()));
                }
            }
        });
        return result;
    }

    private Accessible requireAccess(Long userId, Long studentId) {
        Accessible a = accessible(userId).get(studentId);
        if (a != null && a.student().getSchool().getStatus() != org.afritechinnovations.model.common.SchoolStatus.ACTIVE)
            throw new AccessDeniedException("Cet établissement est désactivé");
        if (a == null) {
            throw new AccessDeniedException("Vous n'avez pas accès au dossier de cet élève");
        }
        return a;
    }

    // ------------------------------------------------------------------ utilitaires

    private SelfServiceDto.StudentOverview overview(Student student, String relationship) {
        Optional<StudentEnrollment> enrollment = currentEnrollment(student.getId());
        SchoolClass cls = enrollment.map(StudentEnrollment::getSchoolClass).orElse(null);
        AcademicYear year = cls == null ? null : cls.getAcademicYear();

        long absences = 0;
        long unjustified = 0;
        long lates = 0;
        for (Attendance a : attendanceRepository.findByStudentIdOrderByAttendanceDateDesc(student.getId())) {
            if(!org.afritechinnovations.service.academic.SelectedAcademicYear.matches(a.getSchoolClass().getAcademicYear())) continue;
            if (year != null && (a.getAttendanceDate().isBefore(year.getStartDate())
                    || a.getAttendanceDate().isAfter(year.getEndDate()))) {
                continue;
            }
            if (a.getStatus() == AttendanceStatus.ABSENT || a.getStatus() == AttendanceStatus.EXCUSED) {
                absences++;
                if (a.getStatus() == AttendanceStatus.ABSENT && (a.getJustification() == null
                        || a.getJustification().isBlank())) {
                    unjustified++;
                }
            } else if (a.getStatus() == AttendanceStatus.LATE) {
                lates++;
            }
        }

        BigDecimal due = BigDecimal.ZERO;
        BigDecimal paid = BigDecimal.ZERO;
        int overdue = 0;
        LocalDate today = LocalDate.now();
        for (Invoice invoice : invoiceRepository.findByStudentId(student.getId())) {
            if (!invoiceInView(invoice) || invoice.getStatus() == InvoiceStatus.CANCELLED) {
                continue;
            }
            BigDecimal net = invoice.getAmountDue().subtract(nz(invoice.getDiscountAmount()));
            BigDecimal invoicePaid = paid(invoice);
            due = due.add(net);
            paid = paid.add(invoicePaid);
            if (invoice.getStatus() != InvoiceStatus.PAID && invoice.getDueDate().isBefore(today)
                    && invoicePaid.compareTo(net) < 0) {
                overdue++;
            }
        }

        return new SelfServiceDto.StudentOverview(student.getId(), fullName(student), student.getRegistrationNumber(),
                student.getBirthDate(), student.getGender(), relationship, student.getSchool().getId(),
                student.getSchool().getName(), cls == null ? null : cls.getId(), cls == null ? null : cls.getName(),
                cls == null || cls.getLevel() == null ? null : cls.getLevel().getName(),
                year == null ? null : year.getLabel(),
                new SelfServiceDto.AttendanceSummary(absences, unjustified, lates),
                new SelfServiceDto.FeeSummary(due, paid, due.subtract(paid).max(BigDecimal.ZERO), overdue));
    }

    /** Inscription active ; à défaut la plus récente (année clôturée). */
    private Optional<StudentEnrollment> currentEnrollment(Long studentId) {
        List<StudentEnrollment> enrollments = enrollmentRepository.findByStudentId(studentId).stream()
                .filter(e -> org.afritechinnovations.service.academic.SelectedAcademicYear.matches(e.getAcademicYear())).toList();
        return enrollments.stream()
                .filter(e -> e.getStatus() == EnrollmentStatus.ACTIVE && (org.afritechinnovations.service.academic.SelectedAcademicYear.id(e.getAcademicYear().getSchool().getId()) != null || Boolean.TRUE.equals(e.getAcademicYear().getIsCurrent())))
                .findFirst()
                .or(() -> enrollments.stream()
                        .filter(e -> e.getStatus() == EnrollmentStatus.COMPLETED
                                || e.getStatus() == EnrollmentStatus.GRADUATED)
                        .max(Comparator.comparing((StudentEnrollment e) -> e.getAcademicYear().getStartDate())));
    }

    private boolean invoiceInView(Invoice invoice) {
        if(org.afritechinnovations.service.academic.SelectedAcademicYear.matches(invoice.getAcademicYear())) return true;
        Long yearId = org.afritechinnovations.service.academic.SelectedAcademicYear.id(invoice.getStudent().getSchool().getId());
        return yearId != null && invoiceRepository.carriedInvoiceIds(yearId).contains(invoice.getId());
    }

    private BigDecimal paid(Invoice invoice) {
        return paymentRepository.findByInvoiceId(invoice.getId()).stream()
                .map(Payment::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private static BigDecimal nz(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }

    private static String fullName(Student student) {
        return TeacherSpaceService.fullName(student.getUser());
    }
}
