package org.afritechinnovations.service.people;

import lombok.RequiredArgsConstructor;
import org.afritechinnovations.dto.people.ClassRosterRowDto;
import org.afritechinnovations.dto.people.CreateRosterStudentRequest;
import org.afritechinnovations.dto.people.CreateStudentInvoiceRequest;
import org.afritechinnovations.dto.people.CreateStudentPaymentRequest;
import org.afritechinnovations.dto.people.StudentDetailDto;
import org.afritechinnovations.dto.people.UpdateParentProfileRequest;
import org.afritechinnovations.dto.people.UpdateStudentProfileRequest;
import org.afritechinnovations.dto.people.UpsertAttendanceRequest;
import org.afritechinnovations.model.academic.AcademicYear;
import org.afritechinnovations.model.academic.ClassSubjectTeacher;
import org.afritechinnovations.model.academic.Grade;
import org.afritechinnovations.model.academic.SchoolClass;
import org.afritechinnovations.model.academic.Attendance;
import org.afritechinnovations.model.common.Role;
import org.afritechinnovations.model.common.SchoolUser;
import org.afritechinnovations.model.common.User;
import org.afritechinnovations.model.finance.FeeType;
import org.afritechinnovations.model.finance.Invoice;
import org.afritechinnovations.model.finance.InvoiceStatus;
import org.afritechinnovations.model.finance.Payment;
import org.afritechinnovations.model.people.EnrollmentStatus;
import org.afritechinnovations.model.people.Parent;
import org.afritechinnovations.model.people.ParentStudent;
import org.afritechinnovations.model.people.Student;
import org.afritechinnovations.model.people.StudentEnrollment;
import org.afritechinnovations.repository.academic.AcademicYearRepository;
import org.afritechinnovations.repository.academic.AttendanceRepository;
import org.afritechinnovations.repository.academic.ClassSubjectTeacherRepository;
import org.afritechinnovations.repository.academic.GradeRepository;
import org.afritechinnovations.repository.academic.SchoolClassRepository;
import org.afritechinnovations.repository.common.RoleRepository;
import org.afritechinnovations.repository.common.SchoolUserRepository;
import org.afritechinnovations.repository.common.UserRepository;
import org.afritechinnovations.repository.finance.FeeTypeRepository;
import org.afritechinnovations.repository.finance.InvoiceRepository;
import org.afritechinnovations.repository.finance.PaymentRepository;
import org.afritechinnovations.repository.people.ParentRepository;
import org.afritechinnovations.repository.people.ParentStudentRepository;
import org.afritechinnovations.repository.people.StudentEnrollmentRepository;
import org.afritechinnovations.repository.people.StudentRepository;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional
public class ClassRosterService {

    private final SchoolClassRepository schoolClassRepository;
    private final StudentEnrollmentRepository studentEnrollmentRepository;
    private final ParentStudentRepository parentStudentRepository;
    private final ClassSubjectTeacherRepository classSubjectTeacherRepository;
    private final StudentRepository studentRepository;
    private final ParentRepository parentRepository;
    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final SchoolUserRepository schoolUserRepository;
    private final PasswordEncoder passwordEncoder;
    private final GradeRepository gradeRepository;
    private final AttendanceRepository attendanceRepository;
    private final InvoiceRepository invoiceRepository;
    private final PaymentRepository paymentRepository;
    private final FeeTypeRepository feeTypeRepository;
    private final AcademicYearRepository academicYearRepository;

    public List<ClassRosterRowDto> getRoster(Long classId, Long ownerId, boolean systemAdmin) {
        requireOwnedClass(classId, ownerId, systemAdmin);
        List<String> teacherNames = teacherNamesForClass(classId);

        return studentEnrollmentRepository
                .findActiveStudentsWithUserByClassId(classId, EnrollmentStatus.ACTIVE)
                .stream()
                .map(StudentEnrollment::getStudent)
                .map(student -> toRow(student, teacherNames))
                .toList();
    }

    public ClassRosterRowDto updateStudentProfile(Long classId, Long studentId, UpdateStudentProfileRequest request,
                                                   Long ownerId, boolean systemAdmin) {
        requireOwnedClass(classId, ownerId, systemAdmin);
        Student student = studentRepository.findById(studentId)
                .orElseThrow(() -> new IllegalArgumentException("Élève introuvable: " + studentId));
        if (!systemAdmin && !student.getSchool().getOwner().getId().equals(ownerId)) {
            throw new AccessDeniedException("Vous ne pouvez modifier que les élèves de votre établissement");
        }

        User user = userRepository.findById(student.getUser().getId())
                .orElseThrow(() -> new IllegalArgumentException("Compte introuvable"));
        user.setFirstName(request.getFirstName().trim());
        user.setLastName(request.getLastName().trim());
        user.setEmail(request.getEmail().trim().toLowerCase());
        user.setPhone(request.getPhone());
        userRepository.save(user);

        student.setRegistrationNumber(request.getRegistrationNumber().trim());
        student.setBirthDate(request.getBirthDate());
        student.setGender(request.getGender());
        studentRepository.save(student);

        return toRow(student, teacherNamesForClass(classId));
    }

    public ClassRosterRowDto updateParentProfile(Long classId, Long parentId, UpdateParentProfileRequest request,
                                                  Long ownerId, boolean systemAdmin) {
        requireOwnedClass(classId, ownerId, systemAdmin);
        Parent parent = parentRepository.findById(parentId)
                .orElseThrow(() -> new IllegalArgumentException("Parent introuvable: " + parentId));

        Student studentInClass = parentStudentRepository.findByParentId(parentId).stream()
                .map(ParentStudent::getStudent)
                .filter(student -> studentEnrollmentRepository.findByStudentId(student.getId()).stream()
                        .anyMatch(enrollment -> enrollment.getStatus() == EnrollmentStatus.ACTIVE
                                && enrollment.getSchoolClass().getId().equals(classId)))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Ce parent n'est pas rattaché à un élève de cette classe"));

        if (!systemAdmin && !studentInClass.getSchool().getOwner().getId().equals(ownerId)) {
            throw new AccessDeniedException("Vous ne pouvez modifier que les parents de votre établissement");
        }

        User user = userRepository.findById(parent.getUser().getId())
                .orElseThrow(() -> new IllegalArgumentException("Compte introuvable"));
        user.setFirstName(request.getFirstName().trim());
        user.setLastName(request.getLastName().trim());
        user.setEmail(request.getEmail().trim().toLowerCase());
        user.setPhone(request.getPhone());
        userRepository.save(user);

        return toRow(studentInClass, teacherNamesForClass(classId));
    }

    public void removeStudentFromClass(Long classId, Long studentId, Long ownerId, boolean systemAdmin) {
        SchoolClass schoolClass = requireOwnedClass(classId, ownerId, systemAdmin);
        Student student = studentRepository.findById(studentId)
                .orElseThrow(() -> new IllegalArgumentException("Élève introuvable: " + studentId));
        boolean enrolledInClass = studentEnrollmentRepository.findByStudentId(studentId).stream()
                .anyMatch(enrollment -> enrollment.getSchoolClass().getId().equals(classId));
        if (!enrolledInClass) {
            throw new IllegalArgumentException("Cet élève n'est pas inscrit dans cette classe");
        }
        if (!systemAdmin && !student.getSchool().getId().equals(schoolClass.getSchool().getId())) {
            throw new AccessDeniedException("Vous ne pouvez supprimer que les élèves de votre établissement");
        }
        // Supprime le compte utilisateur de l'élève : la suppression est répercutée en cascade
        // (students, student_enrollments, parent_student, grades, attendances, report_cards, invoices).
        userRepository.deleteById(student.getUser().getId());
    }

    public ClassRosterRowDto createStudent(Long classId, CreateRosterStudentRequest request,
                                            Long ownerId, boolean systemAdmin) {
        SchoolClass schoolClass = requireOwnedClass(classId, ownerId, systemAdmin);
        String email = request.getEmail().trim().toLowerCase();
        if (userRepository.existsByEmailIgnoreCase(email)) {
            throw new IllegalArgumentException("Un utilisateur existe déjà avec cet email: " + email);
        }
        if (studentRepository.findBySchoolIdAndRegistrationNumber(schoolClass.getSchool().getId(),
                request.getRegistrationNumber().trim()).isPresent()) {
            throw new IllegalArgumentException("Ce matricule est déjà utilisé dans cet établissement");
        }

        User user = User.builder()
                .firstName(request.getFirstName().trim())
                .lastName(request.getLastName().trim())
                .email(email)
                .passwordHash(passwordEncoder.encode(request.getPassword()))
                .phone(request.getPhone())
                .active(true)
                .approved(true)
                .build();
        user = userRepository.save(user);

        Role studentRole = roleRepository.findByName("STUDENT")
                .orElseThrow(() -> new IllegalStateException("Rôle non configuré: STUDENT"));
        schoolUserRepository.save(SchoolUser.builder()
                .user(user)
                .school(schoolClass.getSchool())
                .role(studentRole)
                .build());

        Student student = Student.builder()
                .user(user)
                .school(schoolClass.getSchool())
                .registrationNumber(request.getRegistrationNumber().trim())
                .birthDate(request.getBirthDate())
                .gender(request.getGender())
                .build();
        student = studentRepository.save(student);

        studentEnrollmentRepository.save(StudentEnrollment.builder()
                .student(student)
                .schoolClass(schoolClass)
                .academicYear(schoolClass.getAcademicYear())
                .status(EnrollmentStatus.ACTIVE)
                .enrollmentDate(LocalDate.now())
                .build());

        return toRow(student, teacherNamesForClass(classId));
    }

    public StudentDetailDto getStudentDetail(Long studentId, Long ownerId, boolean systemAdmin) {
        Student student = studentRepository.findById(studentId)
                .orElseThrow(() -> new IllegalArgumentException("Élève introuvable: " + studentId));
        if (!systemAdmin && !student.getSchool().getOwner().getId().equals(ownerId)) {
            throw new AccessDeniedException("Vous ne pouvez consulter que les élèves de votre établissement");
        }

        User user = student.getUser();

        StudentEnrollment activeEnrollment = studentEnrollmentRepository.findByStudentId(studentId).stream()
                .filter(enrollment -> enrollment.getStatus() == EnrollmentStatus.ACTIVE)
                .findFirst()
                .orElse(null);
        String className = activeEnrollment != null ? activeEnrollment.getSchoolClass().getName() : null;

        List<ClassRosterRowDto.ParentInfo> parents = parentStudentRepository
                .findByStudentIdWithParentUser(studentId)
                .stream()
                .map(ps -> {
                    Parent parent = ps.getParent();
                    User parentUser = parent.getUser();
                    return new ClassRosterRowDto.ParentInfo(
                            parent.getId(),
                            parentUser.getId(),
                            parentUser.getFirstName(),
                            parentUser.getLastName(),
                            parentUser.getEmail(),
                            parentUser.getPhone(),
                            ps.getRelationship()
                    );
                })
                .toList();

        List<Grade> grades = gradeRepository.findByStudentIdOrderByGradeDateAsc(studentId);
        List<StudentDetailDto.GradeInfo> gradeInfos = grades.stream()
                .map(grade -> {
                    ClassSubjectTeacher cst = grade.getClassSubjectTeacher();
                    return new StudentDetailDto.GradeInfo(
                            grade.getId(),
                            cst.getSubject().getName(),
                            cst.getTeacher().getUser().getFirstName() + " " + cst.getTeacher().getUser().getLastName(),
                            grade.getTerm(),
                            grade.getType(),
                            grade.getValue(),
                            grade.getMaxValue(),
                            grade.getGradeDate()
                    );
                })
                .sorted(Comparator.comparing(StudentDetailDto.GradeInfo::gradeDate,
                        Comparator.nullsLast(Comparator.reverseOrder())))
                .toList();

        java.util.OptionalDouble averageOpt = grades.stream()
                .filter(g -> g.getValue() != null && g.getMaxValue() != null
                        && g.getMaxValue().compareTo(BigDecimal.ZERO) != 0)
                .mapToDouble(g -> g.getValue().doubleValue() / g.getMaxValue().doubleValue() * 20)
                .average();
        Double overallAverage = averageOpt.isPresent() ? averageOpt.getAsDouble() : null;

        List<Attendance> attendances = attendanceRepository.findByStudentIdOrderByAttendanceDateDesc(studentId);
        long present = attendances.stream().filter(a -> a.getStatus() == org.afritechinnovations.model.academic.AttendanceStatus.PRESENT).count();
        long late = attendances.stream().filter(a -> a.getStatus() == org.afritechinnovations.model.academic.AttendanceStatus.LATE).count();
        // Une absence est "justifiée" si son statut est EXCUSED ou si un motif de justification a été renseigné.
        long justifiedAbsences = attendances.stream()
                .filter(a -> a.getStatus() == org.afritechinnovations.model.academic.AttendanceStatus.ABSENT
                        || a.getStatus() == org.afritechinnovations.model.academic.AttendanceStatus.EXCUSED)
                .filter(a -> a.getStatus() == org.afritechinnovations.model.academic.AttendanceStatus.EXCUSED
                        || (a.getJustification() != null && !a.getJustification().isBlank()))
                .count();
        long absent = attendances.stream()
                .filter(a -> a.getStatus() == org.afritechinnovations.model.academic.AttendanceStatus.ABSENT
                        || a.getStatus() == org.afritechinnovations.model.academic.AttendanceStatus.EXCUSED)
                .count();
        long unjustifiedAbsences = absent - justifiedAbsences;
        long total = attendances.size();
        double attendanceRate = total > 0 ? (present * 100.0 / total) : 0.0;

        StudentDetailDto.AttendanceSummary summary = new StudentDetailDto.AttendanceSummary(
                total, present, absent, late, justifiedAbsences, Math.max(unjustifiedAbsences, 0), attendanceRate);

        List<StudentDetailDto.AttendanceInfo> recentAttendance = attendances.stream()
                .limit(30)
                .map(a -> new StudentDetailDto.AttendanceInfo(a.getId(), a.getAttendanceDate(), a.getStatus().name(), a.getJustification()))
                .toList();

        List<StudentDetailDto.InvoiceInfo> invoiceInfos = buildInvoiceInfos(studentId);
        StudentDetailDto.BillingSummary billingSummary = buildBillingSummary(invoiceInfos);

        return new StudentDetailDto(
                student.getId(),
                user.getId(),
                user.getFirstName(),
                user.getLastName(),
                user.getEmail(),
                user.getPhone(),
                student.getRegistrationNumber(),
                student.getBirthDate(),
                student.getGender(),
                student.getSchool().getName(),
                student.getSchool().getId(),
                className,
                parents,
                gradeInfos,
                overallAverage,
                summary,
                recentAttendance,
                invoiceInfos,
                billingSummary
        );
    }

    public StudentDetailDto.AttendanceInfo addAttendance(Long studentId, UpsertAttendanceRequest request,
                                                          Long ownerId, boolean systemAdmin) {
        Student student = requireOwnedStudent(studentId, ownerId, systemAdmin);
        StudentEnrollment activeEnrollment = studentEnrollmentRepository.findByStudentId(studentId).stream()
                .filter(enrollment -> enrollment.getStatus() == EnrollmentStatus.ACTIVE)
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Cet élève n'a pas de classe active"));

        Attendance attendance = Attendance.builder()
                .student(student)
                .schoolClass(activeEnrollment.getSchoolClass())
                .attendanceDate(request.getAttendanceDate())
                .status(request.getStatus())
                .justification(request.getJustification())
                .build();
        attendance = attendanceRepository.save(attendance);
        return toAttendanceInfo(attendance);
    }

    public StudentDetailDto.AttendanceInfo updateAttendance(Long studentId, Long attendanceId,
                                                             UpsertAttendanceRequest request,
                                                             Long ownerId, boolean systemAdmin) {
        requireOwnedStudent(studentId, ownerId, systemAdmin);
        Attendance attendance = attendanceRepository.findById(attendanceId)
                .orElseThrow(() -> new IllegalArgumentException("Présence introuvable: " + attendanceId));
        if (!attendance.getStudent().getId().equals(studentId)) {
            throw new IllegalArgumentException("Cette présence n'appartient pas à cet élève");
        }
        attendance.setAttendanceDate(request.getAttendanceDate());
        attendance.setStatus(request.getStatus());
        attendance.setJustification(request.getJustification());
        attendance = attendanceRepository.save(attendance);
        return toAttendanceInfo(attendance);
    }

    public void deleteAttendance(Long studentId, Long attendanceId, Long ownerId, boolean systemAdmin) {
        requireOwnedStudent(studentId, ownerId, systemAdmin);
        Attendance attendance = attendanceRepository.findById(attendanceId)
                .orElseThrow(() -> new IllegalArgumentException("Présence introuvable: " + attendanceId));
        if (!attendance.getStudent().getId().equals(studentId)) {
            throw new IllegalArgumentException("Cette présence n'appartient pas à cet élève");
        }
        attendanceRepository.deleteById(attendanceId);
    }

    public StudentDetailDto.InvoiceInfo createInvoice(Long studentId, CreateStudentInvoiceRequest request,
                                                       Long ownerId, boolean systemAdmin) {
        Student student = requireOwnedStudent(studentId, ownerId, systemAdmin);
        FeeType feeType = feeTypeRepository.findById(request.getFeeTypeId())
                .orElseThrow(() -> new IllegalArgumentException("Type de frais introuvable: " + request.getFeeTypeId()));
        if (!feeType.getSchool().getId().equals(student.getSchool().getId())) {
            throw new AccessDeniedException("Ce type de frais n'appartient pas à l'établissement de l'élève");
        }
        if (!feeType.isActive()) {
            throw new IllegalArgumentException("Ce type de frais est archivé");
        }

        AcademicYear academicYear = request.getAcademicYearId() != null
                ? academicYearRepository.findById(request.getAcademicYearId())
                        .orElseThrow(() -> new IllegalArgumentException("Année scolaire introuvable"))
                : academicYearRepository.findBySchoolIdAndIsCurrentTrue(student.getSchool().getId())
                        .orElseThrow(() -> new IllegalArgumentException("Aucune année scolaire courante définie pour cet établissement"));

        Invoice invoice = Invoice.builder()
                .student(student)
                .feeType(feeType)
                .academicYear(academicYear)
                .amountDue(request.getAmountDue() != null ? request.getAmountDue() : feeType.getAmount())
                .dueDate(request.getDueDate())
                .status(InvoiceStatus.PENDING)
                .build();
        invoice = invoiceRepository.save(invoice);
        return toInvoiceInfo(invoice, List.of());
    }

    public StudentDetailDto.InvoiceInfo addPayment(Long studentId, Long invoiceId, CreateStudentPaymentRequest request,
                                                    Long ownerId, boolean systemAdmin) {
        Invoice invoice = requireOwnedInvoice(studentId, invoiceId, ownerId, systemAdmin);
        if (invoice.getStatus() == InvoiceStatus.CANCELLED) {
            throw new IllegalArgumentException("Impossible d'encaisser un frais annulé");
        }
        validatePaymentAmount(request.getAmount(), invoice, null);

        Payment payment = Payment.builder()
                .invoice(invoice)
                .amount(request.getAmount())
                .paymentDate(request.getPaymentDate() != null ? request.getPaymentDate() : LocalDate.now())
                .method(request.getMethod())
                .reference(generatePaymentReference())
                .build();
        paymentRepository.save(payment);

        List<Payment> payments = paymentRepository.findByInvoiceId(invoiceId);
        updateInvoicePaymentStatus(invoice, payments);
        return toInvoiceInfo(invoice, payments);
    }

    public StudentDetailDto.InvoiceInfo updatePayment(Long studentId, Long invoiceId, Long paymentId,
                                                       CreateStudentPaymentRequest request, Long ownerId, boolean systemAdmin) {
        Invoice invoice = requireOwnedInvoice(studentId, invoiceId, ownerId, systemAdmin);
        Payment payment = requireInvoicePayment(invoiceId, paymentId);
        validatePaymentAmount(request.getAmount(), invoice, paymentId);
        payment.setAmount(request.getAmount());
        payment.setPaymentDate(request.getPaymentDate() != null ? request.getPaymentDate() : payment.getPaymentDate());
        payment.setMethod(request.getMethod());
        paymentRepository.save(payment);
        List<Payment> payments = paymentRepository.findByInvoiceId(invoiceId);
        updateInvoicePaymentStatus(invoice, payments);
        return toInvoiceInfo(invoice, payments);
    }

    public void deletePayment(Long studentId, Long invoiceId, Long paymentId, Long ownerId, boolean systemAdmin) {
        Invoice invoice = requireOwnedInvoice(studentId, invoiceId, ownerId, systemAdmin);
        Payment payment = requireInvoicePayment(invoiceId, paymentId);
        paymentRepository.delete(payment);
        updateInvoicePaymentStatus(invoice, paymentRepository.findByInvoiceId(invoiceId));
    }

    private Invoice requireOwnedInvoice(Long studentId, Long invoiceId, Long ownerId, boolean systemAdmin) {
        requireOwnedStudent(studentId, ownerId, systemAdmin);
        Invoice invoice = invoiceRepository.findById(invoiceId)
                .orElseThrow(() -> new IllegalArgumentException("Facture introuvable: " + invoiceId));
        if (!invoice.getStudent().getId().equals(studentId)) {
            throw new IllegalArgumentException("Cette facture n'appartient pas à cet élève");
        }
        return invoice;
    }

    private Payment requireInvoicePayment(Long invoiceId, Long paymentId) {
        Payment payment = paymentRepository.findById(paymentId)
                .orElseThrow(() -> new IllegalArgumentException("Paiement introuvable: " + paymentId));
        if (!payment.getInvoice().getId().equals(invoiceId)) {
            throw new IllegalArgumentException("Ce paiement n'appartient pas à ce frais");
        }
        return payment;
    }

    private void validatePaymentAmount(BigDecimal amount, Invoice invoice, Long excludedPaymentId) {
        if (amount == null || amount.signum() <= 0) {
            throw new IllegalArgumentException("Le montant doit être supérieur à zéro");
        }
        BigDecimal otherPayments = paymentRepository.findByInvoiceId(invoice.getId()).stream()
                .filter(payment -> !payment.getId().equals(excludedPaymentId))
                .map(Payment::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        if (otherPayments.add(amount).compareTo(invoice.netAmount()) > 0) {
            throw new IllegalArgumentException("Le total payé ne peut pas dépasser le montant dû");
        }
    }

    private void updateInvoicePaymentStatus(Invoice invoice, List<Payment> payments) {
        if (invoice.getStatus() == InvoiceStatus.CANCELLED) {
            return;
        }
        BigDecimal totalPaid = payments.stream().map(Payment::getAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
        invoice.setStatus(totalPaid.compareTo(invoice.netAmount()) >= 0 ? InvoiceStatus.PAID
                : invoice.getDueDate().isBefore(LocalDate.now()) ? InvoiceStatus.OVERDUE : InvoiceStatus.PENDING);
        invoiceRepository.save(invoice);
    }

    public void deleteInvoice(Long studentId, Long invoiceId, Long ownerId, boolean systemAdmin) {
        requireOwnedStudent(studentId, ownerId, systemAdmin);
        Invoice invoice = invoiceRepository.findById(invoiceId)
                .orElseThrow(() -> new IllegalArgumentException("Facture introuvable: " + invoiceId));
        if (!invoice.getStudent().getId().equals(studentId)) {
            throw new IllegalArgumentException("Cette facture n'appartient pas à cet élève");
        }
        invoiceRepository.deleteById(invoiceId);
    }

    public StudentDetailDto.InvoiceInfo cancelInvoice(Long studentId, Long invoiceId, Long ownerId, boolean systemAdmin) {
        requireOwnedStudent(studentId, ownerId, systemAdmin);
        Invoice invoice = invoiceRepository.findById(invoiceId)
                .orElseThrow(() -> new IllegalArgumentException("Facture introuvable: " + invoiceId));
        if (!invoice.getStudent().getId().equals(studentId)) {
            throw new IllegalArgumentException("Cette facture n'appartient pas à cet élève");
        }
        invoice.setStatus(InvoiceStatus.CANCELLED);
        invoice = invoiceRepository.save(invoice);
        return toInvoiceInfo(invoice, paymentRepository.findByInvoiceId(invoiceId));
    }

    private synchronized String generatePaymentReference() {
        String yearPrefix = String.valueOf(LocalDate.now().getYear());
        long countThisYear = paymentRepository.countByReferenceStartingWith(yearPrefix + "-");
        String reference;
        do {
            reference = yearPrefix + "-" + String.format("%04d", ++countThisYear);
        } while (paymentRepository.existsByReference(reference));
        return reference;
    }

    private Student requireOwnedStudent(Long studentId, Long ownerId, boolean systemAdmin) {
        Student student = studentRepository.findById(studentId)
                .orElseThrow(() -> new IllegalArgumentException("Élève introuvable: " + studentId));
        if (!systemAdmin && !student.getSchool().getOwner().getId().equals(ownerId)) {
            throw new AccessDeniedException("Vous ne pouvez gérer que les élèves de votre établissement");
        }
        return student;
    }

    private StudentDetailDto.AttendanceInfo toAttendanceInfo(Attendance attendance) {
        return new StudentDetailDto.AttendanceInfo(
                attendance.getId(), attendance.getAttendanceDate(), attendance.getStatus().name(), attendance.getJustification());
    }

    private List<StudentDetailDto.InvoiceInfo> buildInvoiceInfos(Long studentId) {
        return invoiceRepository.findByStudentId(studentId).stream()
                .map(invoice -> toInvoiceInfo(invoice, paymentRepository.findByInvoiceId(invoice.getId())))
                .sorted(Comparator.comparing(StudentDetailDto.InvoiceInfo::dueDate, Comparator.nullsLast(Comparator.reverseOrder())))
                .toList();
    }

    private StudentDetailDto.InvoiceInfo toInvoiceInfo(Invoice invoice, List<Payment> payments) {
        BigDecimal totalPaid = payments.stream().map(Payment::getAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal balance = invoice.getStatus() == InvoiceStatus.CANCELLED
                ? BigDecimal.ZERO : invoice.netAmount().subtract(totalPaid);
        List<StudentDetailDto.PaymentInfo> paymentInfos = payments.stream()
                .map(p -> new StudentDetailDto.PaymentInfo(p.getId(), p.getAmount(), p.getPaymentDate(),
                        p.getMethod().name(), p.getReference()))
                .toList();
        return new StudentDetailDto.InvoiceInfo(
                invoice.getId(),
                invoice.getFeeType().getId(),
                invoice.getFeeType().getName(),
                invoice.getAmountDue(),
                invoice.getDueDate(),
                invoice.getStatus().name(),
                totalPaid,
                balance,
                paymentInfos,
                invoice.getDiscountAmount() == null ? BigDecimal.ZERO : invoice.getDiscountAmount(),
                invoice.getDiscountReason()
        );
    }

    private StudentDetailDto.BillingSummary buildBillingSummary(List<StudentDetailDto.InvoiceInfo> invoices) {
        BigDecimal totalDue = invoices.stream()
                .filter(invoice -> !InvoiceStatus.CANCELLED.name().equals(invoice.status()))
                .map(invoice -> invoice.amountDue().subtract(invoice.discountAmount())).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal totalPaid = invoices.stream().map(StudentDetailDto.InvoiceInfo::totalPaid).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal totalBalance = invoices.stream().map(StudentDetailDto.InvoiceInfo::balance).reduce(BigDecimal.ZERO, BigDecimal::add);
        return new StudentDetailDto.BillingSummary(totalDue, totalPaid, totalBalance);
    }

    private SchoolClass requireOwnedClass(Long classId, Long ownerId, boolean systemAdmin) {
        SchoolClass schoolClass = schoolClassRepository.findById(classId)
                .orElseThrow(() -> new IllegalArgumentException("Classe introuvable: " + classId));
        if (!systemAdmin && !schoolClass.getSchool().getOwner().getId().equals(ownerId)) {
            throw new AccessDeniedException("Vous ne pouvez consulter que les classes de votre établissement");
        }
        return schoolClass;
    }

    private List<String> teacherNamesForClass(Long classId) {
        return classSubjectTeacherRepository.findAllWithTeacherByClassId(classId)
                .stream()
                .filter(ClassSubjectTeacher::isActive)
                .map(ClassSubjectTeacher::getTeacher)
                .map(teacher -> teacher.getUser().getFirstName() + " " + teacher.getUser().getLastName())
                .distinct()
                .toList();
    }

    private ClassRosterRowDto toRow(Student student, List<String> teacherNames) {
        User user = student.getUser();
        List<ClassRosterRowDto.ParentInfo> parents = parentStudentRepository
                .findByStudentIdWithParentUser(student.getId())
                .stream()
                .map(ps -> {
                    Parent parent = ps.getParent();
                    User parentUser = parent.getUser();
                    return new ClassRosterRowDto.ParentInfo(
                            parent.getId(),
                            parentUser.getId(),
                            parentUser.getFirstName(),
                            parentUser.getLastName(),
                            parentUser.getEmail(),
                            parentUser.getPhone(),
                            ps.getRelationship()
                    );
                })
                .toList();

        return new ClassRosterRowDto(
                student.getId(),
                user.getId(),
                user.getFirstName(),
                user.getLastName(),
                user.getEmail(),
                user.getPhone(),
                student.getRegistrationNumber(),
                student.getBirthDate(),
                student.getGender(),
                parents,
                teacherNames
        );
    }
}
