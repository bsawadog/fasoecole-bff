package org.afritechinnovations.service.finance;

import lombok.RequiredArgsConstructor;
import org.afritechinnovations.dto.finance.OwnerFinanceDto;
import org.afritechinnovations.model.academic.AcademicYear;
import org.afritechinnovations.model.academic.Level;
import org.afritechinnovations.model.academic.SchoolClass;
import org.afritechinnovations.model.common.School;
import org.afritechinnovations.model.finance.FeeFrequency;
import org.afritechinnovations.model.finance.FeeType;
import org.afritechinnovations.model.finance.Invoice;
import org.afritechinnovations.model.finance.InvoiceStatus;
import org.afritechinnovations.model.finance.Payment;
import org.afritechinnovations.model.people.EnrollmentStatus;
import org.afritechinnovations.model.people.Student;
import org.afritechinnovations.model.people.StudentEnrollment;
import org.afritechinnovations.repository.academic.AcademicYearRepository;
import org.afritechinnovations.repository.academic.LevelRepository;
import org.afritechinnovations.repository.academic.SchoolClassRepository;
import org.afritechinnovations.repository.common.SchoolRepository;
import org.afritechinnovations.repository.finance.FeeTypeRepository;
import org.afritechinnovations.repository.finance.InvoiceRepository;
import org.afritechinnovations.repository.finance.PaymentRepository;
import org.afritechinnovations.repository.people.ParentStudentRepository;
import org.afritechinnovations.repository.people.StudentEnrollmentRepository;
import org.afritechinnovations.service.common.EmailService;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/** Pilotage financier d'un établissement par son propriétaire : catalogue, facturation, encaissements. */
@Service
@RequiredArgsConstructor
@Transactional
public class OwnerFinanceService {

    private final SchoolRepository schoolRepository;
    private final FeeTypeRepository feeTypeRepository;
    private final InvoiceRepository invoiceRepository;
    private final PaymentRepository paymentRepository;
    private final LevelRepository levelRepository;
    private final SchoolClassRepository schoolClassRepository;
    private final AcademicYearRepository academicYearRepository;
    private final StudentEnrollmentRepository studentEnrollmentRepository;
    private final ParentStudentRepository parentStudentRepository;
    private final EmailService emailService;

    private Clock clock = Clock.systemDefaultZone();

    void setClock(Clock clock) {
        this.clock = clock;
    }

    // ---------------------------------------------------------------- catalogue

    @Transactional(readOnly = true)
    public List<OwnerFinanceDto.FeeTypeInfo> listFeeTypes(Long schoolId, Long ownerId, boolean systemAdmin) {
        requireOwnedSchool(schoolId, ownerId, systemAdmin);
        Map<Long, Long> counts = new HashMap<>();
        for (Object[] row : invoiceRepository.countByFeeTypeForSchool(schoolId)) {
            counts.put((Long) row[0], ((Number) row[1]).longValue());
        }
        return feeTypeRepository.findWithLevelBySchoolId(schoolId).stream()
                .map(fee -> toFeeTypeInfo(fee, counts.getOrDefault(fee.getId(), 0L)))
                .toList();
    }

    public OwnerFinanceDto.FeeTypeInfo createFeeType(Long schoolId, OwnerFinanceDto.FeeTypeRequest request,
                                                     Long ownerId, boolean systemAdmin) {
        School school = requireOwnedSchool(schoolId, ownerId, systemAdmin);
        FeeType fee = FeeType.builder().school(school).build();
        applyFeeType(fee, request, schoolId);
        return toFeeTypeInfo(feeTypeRepository.save(fee), 0);
    }

    public OwnerFinanceDto.FeeTypeInfo updateFeeType(Long feeTypeId, OwnerFinanceDto.FeeTypeRequest request,
                                                     Long ownerId, boolean systemAdmin) {
        FeeType fee = requireOwnedFeeType(feeTypeId, ownerId, systemAdmin);
        applyFeeType(fee, request, fee.getSchool().getId());
        if (request.active() != null) {
            fee.setActive(request.active());
        }
        return toFeeTypeInfo(feeTypeRepository.save(fee), invoiceCount(fee));
    }

    /** Supprime un frais jamais facturé ; sinon l'archive pour conserver l'historique. */
    public boolean deleteFeeType(Long feeTypeId, Long ownerId, boolean systemAdmin) {
        FeeType fee = requireOwnedFeeType(feeTypeId, ownerId, systemAdmin);
        if (invoiceRepository.existsByFeeTypeId(feeTypeId)) {
            fee.setActive(false);
            feeTypeRepository.save(fee);
            return false;
        }
        feeTypeRepository.delete(fee);
        return true;
    }

    // ---------------------------------------------------------------- facturation

    public OwnerFinanceDto.BulkInvoiceResult bulkInvoice(Long schoolId, OwnerFinanceDto.BulkInvoiceRequest request,
                                                         Long ownerId, boolean systemAdmin) {
        requireOwnedSchool(schoolId, ownerId, systemAdmin);
        FeeType fee = requireOwnedFeeType(request.feeTypeId(), ownerId, systemAdmin);
        if (!fee.getSchool().getId().equals(schoolId)) {
            throw new AccessDeniedException("Ce frais n'appartient pas à cet établissement");
        }
        if (!fee.isActive()) {
            throw new IllegalArgumentException("Ce frais est archivé : réactivez-le avant de facturer");
        }
        AcademicYear year = academicYearRepository.findBySchoolIdAndIsCurrentTrue(schoolId)
                .orElseThrow(() -> new IllegalArgumentException("Définissez d'abord l'année scolaire courante de l'établissement"));

        List<SchoolClass> classes;
        if (request.classId() != null) {
            SchoolClass schoolClass = schoolClassRepository.findById(request.classId())
                    .orElseThrow(() -> new IllegalArgumentException("Classe introuvable : " + request.classId()));
            if (!schoolClass.getSchool().getId().equals(schoolId)) {
                throw new AccessDeniedException("Cette classe n'appartient pas à cet établissement");
            }
            classes = List.of(schoolClass);
        } else {
            Long levelId = request.levelId() != null ? request.levelId()
                    : fee.getLevel() != null ? fee.getLevel().getId() : null;
            classes = schoolClassRepository.findAllWithLevelBySchoolAndYear(schoolId, year.getId()).stream()
                    .filter(c -> levelId == null || c.getLevel().getId().equals(levelId))
                    .toList();
        }
        if (classes.isEmpty()) {
            throw new IllegalArgumentException("Aucune classe ne correspond à cette sélection pour l'année en cours");
        }

        BigDecimal amount = request.amount() != null ? request.amount() : fee.getAmount();
        if (amount == null || amount.signum() <= 0) {
            throw new IllegalArgumentException("Le montant à facturer doit être supérieur à zéro");
        }
        Set<Long> studentIds = new LinkedHashSet<>();
        int created = 0;
        int skipped = 0;
        for (SchoolClass schoolClass : classes) {
            for (StudentEnrollment enrollment : studentEnrollmentRepository
                    .findBySchoolClassIdAndStatus(schoolClass.getId(), EnrollmentStatus.ACTIVE)) {
                Student student = enrollment.getStudent();
                if (!studentIds.add(student.getId())) {
                    continue;
                }
                if (invoiceRepository.existsActiveInvoice(student.getId(), fee.getId(), request.dueDate())) {
                    skipped++;
                    continue;
                }
                invoiceRepository.save(Invoice.builder()
                        .student(student)
                        .feeType(fee)
                        .academicYear(year)
                        .amountDue(amount)
                        .dueDate(request.dueDate())
                        .status(request.dueDate().isBefore(today()) ? InvoiceStatus.OVERDUE : InvoiceStatus.PENDING)
                        .build());
                created++;
            }
        }
        return new OwnerFinanceDto.BulkInvoiceResult(created, skipped, studentIds.size());
    }

    public OwnerFinanceDto.InvoiceRow applyDiscount(Long invoiceId, OwnerFinanceDto.DiscountRequest request,
                                                    Long ownerId, boolean systemAdmin) {
        Invoice invoice = requireOwnedInvoice(invoiceId, ownerId, systemAdmin);
        if (invoice.getStatus() == InvoiceStatus.CANCELLED) {
            throw new IllegalArgumentException("Impossible de modifier un frais annulé");
        }
        BigDecimal paid = sum(paymentRepository.findByInvoiceId(invoiceId));
        BigDecimal discount = request.amount().setScale(2, RoundingMode.HALF_UP);
        if (discount.compareTo(invoice.getAmountDue()) > 0) {
            throw new IllegalArgumentException("La réduction ne peut pas dépasser le montant facturé");
        }
        if (paid.add(discount).compareTo(invoice.getAmountDue()) > 0) {
            throw new IllegalArgumentException("La réduction dépasse le reste à payer (déjà versé : " + paid.toPlainString() + ")");
        }
        String reason = request.reason() == null || request.reason().isBlank() ? null : request.reason().trim();
        if (discount.signum() > 0 && reason == null) {
            throw new IllegalArgumentException("Indiquez le motif de la réduction ou de l'exonération");
        }
        invoice.setDiscountAmount(discount);
        invoice.setDiscountReason(discount.signum() == 0 ? null : reason);
        refreshStatus(invoice, paid);
        invoiceRepository.save(invoice);
        return toInvoiceRow(invoice, paid, classLabels(invoice.getStudent().getSchool().getId()));
    }

    /** Résout l'élève d'une facture après contrôle d'appartenance, pour réutiliser l'encaissement existant. */
    @Transactional(readOnly = true)
    public Long studentIdOf(Long invoiceId, Long ownerId, boolean systemAdmin) {
        return requireOwnedInvoice(invoiceId, ownerId, systemAdmin).getStudent().getId();
    }

    // ---------------------------------------------------------------- consultation

    public List<OwnerFinanceDto.InvoiceRow> listInvoices(Long schoolId, String filter, Long classId,
                                                         Long ownerId, boolean systemAdmin) {
        requireOwnedSchool(schoolId, ownerId, systemAdmin);
        Map<Long, ClassLabel> labels = classLabels(schoolId);
        Map<Long, BigDecimal> paidByInvoice = paidByInvoice(schoolId);
        String status = filter == null || filter.isBlank() ? "ALL" : filter.toUpperCase(Locale.ROOT);
        return invoiceRepository.findAllWithDetailsBySchoolId(schoolId).stream()
                .map(invoice -> {
                    BigDecimal paid = paidByInvoice.getOrDefault(invoice.getId(), BigDecimal.ZERO);
                    refreshStatus(invoice, paid);
                    return toInvoiceRow(invoice, paid, labels);
                })
                .filter(row -> classId == null || classId.equals(row.classId()))
                .filter(row -> switch (status) {
                    case "UNPAID" -> row.balance().signum() > 0 && !"CANCELLED".equals(row.status());
                    case "ALL" -> true;
                    default -> status.equals(row.status());
                })
                .toList();
    }

    @Transactional(readOnly = true)
    public List<OwnerFinanceDto.PaymentRow> listPayments(Long schoolId, LocalDate from, LocalDate to,
                                                         Long ownerId, boolean systemAdmin) {
        requireOwnedSchool(schoolId, ownerId, systemAdmin);
        Map<Long, ClassLabel> labels = classLabels(schoolId);
        List<Payment> payments = paymentRepository.findAllWithDetailsBySchoolId(schoolId);
        Map<Long, BigDecimal> paidByInvoice = groupPaid(payments);
        return payments.stream()
                .filter(p -> from == null || !p.getPaymentDate().isBefore(from))
                .filter(p -> to == null || !p.getPaymentDate().isAfter(to))
                .map(p -> toPaymentRow(p, labels, paidByInvoice))
                .toList();
    }

    @Transactional(readOnly = true)
    public String exportPaymentsCsv(Long schoolId, LocalDate from, LocalDate to, Long ownerId, boolean systemAdmin) {
        StringBuilder csv = new StringBuilder("\uFEFFReçu;Date;Élève;Matricule;Classe;Frais;Mode;Montant (FCFA)\n");
        for (OwnerFinanceDto.PaymentRow row : listPayments(schoolId, from, to, ownerId, systemAdmin)) {
            csv.append(csvCell(row.reference())).append(';')
                    .append(row.paymentDate()).append(';')
                    .append(csvCell(row.studentName())).append(';')
                    .append(csvCell(row.registrationNumber())).append(';')
                    .append(csvCell(row.className())).append(';')
                    .append(csvCell(row.feeTypeName())).append(';')
                    .append(methodLabel(row.method())).append(';')
                    .append(row.amount().setScale(0, RoundingMode.HALF_UP).toPlainString()).append('\n');
        }
        return csv.toString();
    }

    public OwnerFinanceDto.Overview overview(Long schoolId, Long ownerId, boolean systemAdmin) {
        School school = requireOwnedSchool(schoolId, ownerId, systemAdmin);
        Map<Long, ClassLabel> labels = classLabels(schoolId);
        List<Payment> payments = paymentRepository.findAllWithDetailsBySchoolId(schoolId);
        Map<Long, BigDecimal> paidByInvoice = groupPaid(payments);
        List<OwnerFinanceDto.InvoiceRow> rows = invoiceRepository.findAllWithDetailsBySchoolId(schoolId).stream()
                .map(invoice -> {
                    BigDecimal paid = paidByInvoice.getOrDefault(invoice.getId(), BigDecimal.ZERO);
                    refreshStatus(invoice, paid);
                    return toInvoiceRow(invoice, paid, labels);
                })
                .filter(row -> !"CANCELLED".equals(row.status()))
                .toList();

        BigDecimal expected = rows.stream().map(OwnerFinanceDto.InvoiceRow::netAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal discounts = rows.stream().map(OwnerFinanceDto.InvoiceRow::discountAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal collected = rows.stream().map(OwnerFinanceDto.InvoiceRow::paid).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal remaining = rows.stream().map(OwnerFinanceDto.InvoiceRow::balance).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal overdue = rows.stream().filter(r -> "OVERDUE".equals(r.status()))
                .map(OwnerFinanceDto.InvoiceRow::balance).reduce(BigDecimal.ZERO, BigDecimal::add);
        LocalDate monthStart = today().withDayOfMonth(1);
        BigDecimal thisMonth = payments.stream()
                .filter(p -> !p.getPaymentDate().isBefore(monthStart) && !p.getPaymentDate().isAfter(today()))
                .map(Payment::getAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
        double rate = expected.signum() == 0 ? 0
                : collected.multiply(BigDecimal.valueOf(100)).divide(expected, 1, RoundingMode.HALF_UP).doubleValue();

        Map<Long, Long> studentsPerClass = labels.values().stream()
                .collect(Collectors.groupingBy(ClassLabel::classId, Collectors.counting()));
        Map<Long, List<OwnerFinanceDto.InvoiceRow>> byClass = rows.stream().filter(r -> r.classId() != null)
                .collect(Collectors.groupingBy(OwnerFinanceDto.InvoiceRow::classId, LinkedHashMap::new, Collectors.toList()));
        Map<Long, String> classNames = new HashMap<>();
        labels.values().forEach(label -> classNames.put(label.classId(), label.className()));
        List<OwnerFinanceDto.ClassBreakdown> perClass = new ArrayList<>();
        for (Map.Entry<Long, String> entry : classNames.entrySet()) {
            List<OwnerFinanceDto.InvoiceRow> classRows = byClass.getOrDefault(entry.getKey(), List.of());
            perClass.add(new OwnerFinanceDto.ClassBreakdown(entry.getKey(), entry.getValue(),
                    studentsPerClass.getOrDefault(entry.getKey(), 0L),
                    total(classRows, OwnerFinanceDto.InvoiceRow::netAmount),
                    total(classRows, OwnerFinanceDto.InvoiceRow::paid),
                    total(classRows, OwnerFinanceDto.InvoiceRow::balance),
                    classRows.stream().filter(r -> r.balance().signum() > 0).count()));
        }
        perClass.sort(Comparator.comparing(OwnerFinanceDto.ClassBreakdown::className));

        Map<Long, List<OwnerFinanceDto.InvoiceRow>> byFee = rows.stream()
                .collect(Collectors.groupingBy(OwnerFinanceDto.InvoiceRow::feeTypeId, LinkedHashMap::new, Collectors.toList()));
        List<OwnerFinanceDto.FeeBreakdown> perFee = byFee.values().stream()
                .map(feeRows -> new OwnerFinanceDto.FeeBreakdown(feeRows.get(0).feeTypeId(), feeRows.get(0).feeTypeName(),
                        total(feeRows, OwnerFinanceDto.InvoiceRow::netAmount),
                        total(feeRows, OwnerFinanceDto.InvoiceRow::paid),
                        total(feeRows, OwnerFinanceDto.InvoiceRow::balance)))
                .sorted(Comparator.comparing(OwnerFinanceDto.FeeBreakdown::feeTypeName))
                .toList();

        List<OwnerFinanceDto.PaymentRow> recent = payments.stream().limit(8)
                .map(p -> toPaymentRow(p, labels, paidByInvoice)).toList();

        return new OwnerFinanceDto.Overview(school.getName(), expected, discounts, collected, remaining, overdue,
                thisMonth, rate, rows.size(),
                rows.stream().filter(r -> r.balance().signum() > 0).count(),
                rows.stream().filter(r -> "OVERDUE".equals(r.status())).count(),
                rows.stream().filter(r -> "PAID".equals(r.status())).count(),
                perClass, perFee, recent);
    }

    // ---------------------------------------------------------------- relances

    @Transactional(readOnly = true)
    public OwnerFinanceDto.ReminderResult sendReminder(Long invoiceId, Long ownerId, boolean systemAdmin) {
        Invoice invoice = requireOwnedInvoice(invoiceId, ownerId, systemAdmin);
        BigDecimal paid = sum(paymentRepository.findByInvoiceId(invoiceId));
        BigDecimal balance = invoice.netAmount().subtract(paid);
        if (invoice.getStatus() == InvoiceStatus.CANCELLED || balance.signum() <= 0) {
            throw new IllegalArgumentException("Ce frais est soldé ou annulé : aucune relance nécessaire");
        }
        Student student = invoice.getStudent();
        Set<String> recipients = new LinkedHashSet<>();
        parentStudentRepository.findByStudentIdWithParentUser(student.getId()).stream()
                .map(link -> link.getParent().getUser().getEmail())
                .filter(email -> email != null && !email.isBlank())
                .forEach(recipients::add);
        if (recipients.isEmpty() && student.getUser().getEmail() != null && !student.getUser().getEmail().isBlank()) {
            recipients.add(student.getUser().getEmail());
        }
        if (recipients.isEmpty()) {
            throw new IllegalArgumentException("Aucune adresse courriel n'est connue pour cet élève ou ses parents");
        }
        String studentName = student.getUser().getFirstName() + " " + student.getUser().getLastName();
        String schoolName = student.getSchool().getName();
        String body = "Bonjour,\n\n"
                + "Sauf erreur de notre part, le frais suivant reste à régler pour " + studentName + " :\n\n"
                + "- Établissement : " + schoolName + "\n"
                + "- Frais : " + invoice.getFeeType().getName() + "\n"
                + "- Échéance : " + invoice.getDueDate() + "\n"
                + "- Montant : " + fcfa(invoice.netAmount()) + "\n"
                + "- Déjà versé : " + fcfa(paid) + "\n"
                + "- Reste à payer : " + fcfa(balance) + "\n\n"
                + "Merci de régulariser la situation auprès de l'administration de l'établissement. "
                + "Si le paiement a déjà été effectué, veuillez ignorer ce message.\n\n"
                + schoolName + " — via FasoÉcole";
        for (String email : recipients) {
            emailService.sendText(email, "Rappel de paiement — " + studentName, body);
        }
        return new OwnerFinanceDto.ReminderResult(recipients.size());
    }

    // ---------------------------------------------------------------- utilitaires

    private void applyFeeType(FeeType fee, OwnerFinanceDto.FeeTypeRequest request, Long schoolId) {
        fee.setName(request.name().trim());
        fee.setAmount(request.amount());
        fee.setFrequency(request.frequency() != null ? request.frequency() : FeeFrequency.ONE_TIME);
        fee.setDescription(request.description() == null || request.description().isBlank() ? null : request.description().trim());
        if (request.levelId() == null) {
            fee.setLevel(null);
        } else {
            Level level = levelRepository.findById(request.levelId())
                    .orElseThrow(() -> new IllegalArgumentException("Niveau introuvable : " + request.levelId()));
            if (!level.getSchool().getId().equals(schoolId)) {
                throw new AccessDeniedException("Ce niveau n'appartient pas à cet établissement");
            }
            fee.setLevel(level);
        }
    }

    private void refreshStatus(Invoice invoice, BigDecimal paid) {
        if (invoice.getStatus() == InvoiceStatus.CANCELLED) {
            return;
        }
        InvoiceStatus status = paid.compareTo(invoice.netAmount()) >= 0 ? InvoiceStatus.PAID
                : invoice.getDueDate().isBefore(today()) ? InvoiceStatus.OVERDUE : InvoiceStatus.PENDING;
        if (status != invoice.getStatus()) {
            invoice.setStatus(status);
        }
    }

    private OwnerFinanceDto.InvoiceRow toInvoiceRow(Invoice invoice, BigDecimal paid, Map<Long, ClassLabel> labels) {
        Student student = invoice.getStudent();
        ClassLabel label = labels.get(student.getId());
        boolean cancelled = invoice.getStatus() == InvoiceStatus.CANCELLED;
        BigDecimal net = invoice.netAmount();
        BigDecimal balance = cancelled ? BigDecimal.ZERO : net.subtract(paid).max(BigDecimal.ZERO);
        long daysOverdue = invoice.getStatus() == InvoiceStatus.OVERDUE
                ? ChronoUnit.DAYS.between(invoice.getDueDate(), today()) : 0;
        return new OwnerFinanceDto.InvoiceRow(invoice.getId(), student.getId(),
                student.getUser().getFirstName() + " " + student.getUser().getLastName(),
                student.getRegistrationNumber(),
                label == null ? null : label.classId(), label == null ? "Sans classe" : label.className(),
                invoice.getFeeType().getId(), invoice.getFeeType().getName(), invoice.getAmountDue(),
                invoice.getDiscountAmount() == null ? BigDecimal.ZERO : invoice.getDiscountAmount(),
                invoice.getDiscountReason(), net, paid, balance, invoice.getDueDate(), invoice.getStatus().name(),
                daysOverdue);
    }

    private OwnerFinanceDto.PaymentRow toPaymentRow(Payment payment, Map<Long, ClassLabel> labels,
                                                    Map<Long, BigDecimal> paidByInvoice) {
        Invoice invoice = payment.getInvoice();
        Student student = invoice.getStudent();
        ClassLabel label = labels.get(student.getId());
        BigDecimal balance = invoice.getStatus() == InvoiceStatus.CANCELLED ? BigDecimal.ZERO
                : invoice.netAmount().subtract(paidByInvoice.getOrDefault(invoice.getId(), BigDecimal.ZERO)).max(BigDecimal.ZERO);
        return new OwnerFinanceDto.PaymentRow(payment.getId(), payment.getReference(), payment.getPaymentDate(),
                payment.getAmount(), payment.getMethod().name(), invoice.getId(), student.getId(),
                student.getUser().getFirstName() + " " + student.getUser().getLastName(),
                student.getRegistrationNumber(), label == null ? "Sans classe" : label.className(),
                invoice.getFeeType().getName(), invoice.netAmount(), balance);
    }

    private OwnerFinanceDto.FeeTypeInfo toFeeTypeInfo(FeeType fee, long invoiceCount) {
        return new OwnerFinanceDto.FeeTypeInfo(fee.getId(), fee.getName(), fee.getAmount(), fee.getFrequency(),
                fee.getLevel() == null ? null : fee.getLevel().getId(),
                fee.getLevel() == null ? null : fee.getLevel().getName(),
                fee.getDescription(), fee.isActive(), invoiceCount);
    }

    private long invoiceCount(FeeType fee) {
        return invoiceRepository.countByFeeTypeForSchool(fee.getSchool().getId()).stream()
                .filter(row -> fee.getId().equals(row[0]))
                .mapToLong(row -> ((Number) row[1]).longValue())
                .findFirst().orElse(0L);
    }

    /** Classe actuelle de chaque élève : l'inscription active la plus récente. */
    private Map<Long, ClassLabel> classLabels(Long schoolId) {
        Map<Long, StudentEnrollment> latest = new HashMap<>();
        for (StudentEnrollment enrollment : studentEnrollmentRepository.findActiveBySchoolId(schoolId)) {
            latest.merge(enrollment.getStudent().getId(), enrollment, (a, b) ->
                    b.getEnrollmentDate().isAfter(a.getEnrollmentDate()) ? b : a);
        }
        Map<Long, ClassLabel> labels = new HashMap<>();
        latest.forEach((studentId, enrollment) -> labels.put(studentId,
                new ClassLabel(enrollment.getSchoolClass().getId(), enrollment.getSchoolClass().getName())));
        return labels;
    }

    private Map<Long, BigDecimal> paidByInvoice(Long schoolId) {
        return groupPaid(paymentRepository.findAllWithDetailsBySchoolId(schoolId));
    }

    private static Map<Long, BigDecimal> groupPaid(List<Payment> payments) {
        Map<Long, BigDecimal> paid = new HashMap<>();
        payments.forEach(p -> paid.merge(p.getInvoice().getId(), p.getAmount(), BigDecimal::add));
        return paid;
    }

    private static BigDecimal sum(List<Payment> payments) {
        return payments.stream().map(Payment::getAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private static BigDecimal total(List<OwnerFinanceDto.InvoiceRow> rows,
                                    java.util.function.Function<OwnerFinanceDto.InvoiceRow, BigDecimal> field) {
        return rows.stream().map(field).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private School requireOwnedSchool(Long schoolId, Long ownerId, boolean systemAdmin) {
        School school = schoolRepository.findById(schoolId)
                .orElseThrow(() -> new IllegalArgumentException("Établissement introuvable : " + schoolId));
        if (!systemAdmin && (school.getOwner() == null || !school.getOwner().getId().equals(ownerId))) {
            throw new AccessDeniedException("Vous ne pouvez gérer que les finances de vos établissements");
        }
        return school;
    }

    private FeeType requireOwnedFeeType(Long feeTypeId, Long ownerId, boolean systemAdmin) {
        FeeType fee = feeTypeRepository.findById(feeTypeId)
                .orElseThrow(() -> new IllegalArgumentException("Frais introuvable : " + feeTypeId));
        requireOwnedSchool(fee.getSchool().getId(), ownerId, systemAdmin);
        return fee;
    }

    private Invoice requireOwnedInvoice(Long invoiceId, Long ownerId, boolean systemAdmin) {
        Invoice invoice = invoiceRepository.findById(invoiceId)
                .orElseThrow(() -> new IllegalArgumentException("Facture introuvable : " + invoiceId));
        requireOwnedSchool(invoice.getStudent().getSchool().getId(), ownerId, systemAdmin);
        return invoice;
    }

    private LocalDate today() {
        return LocalDate.now(clock);
    }

    private static String csvCell(String value) {
        if (value == null) {
            return "";
        }
        String safe = value.replace("\"", "\"\"");
        if (!safe.isEmpty() && "=+-@".indexOf(safe.charAt(0)) >= 0) {
            safe = "'" + safe;
        }
        return safe.contains(";") || safe.contains("\"") || safe.contains("\n") ? "\"" + safe + "\"" : safe;
    }

    private static String methodLabel(String method) {
        return switch (method) {
            case "CASH" -> "Espèces";
            case "MOBILE_MONEY" -> "Mobile Money";
            case "BANK_TRANSFER" -> "Virement";
            case "CARD" -> "Carte";
            default -> method;
        };
    }

    private static String fcfa(BigDecimal value) {
        return String.format(Locale.FRANCE, "%,.0f FCFA", value).replace('\u202f', ' ').replace('\u00a0', ' ');
    }

    private record ClassLabel(Long classId, String className) {
    }
}
