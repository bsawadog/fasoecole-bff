package org.afritechinnovations.service.finance;

import org.afritechinnovations.dto.finance.OwnerFinanceDto;
import org.afritechinnovations.model.academic.AcademicYear;
import org.afritechinnovations.model.academic.Level;
import org.afritechinnovations.model.academic.SchoolClass;
import org.afritechinnovations.model.common.School;
import org.afritechinnovations.model.common.User;
import org.afritechinnovations.model.finance.FeeType;
import org.afritechinnovations.model.finance.Invoice;
import org.afritechinnovations.model.finance.InvoiceStatus;
import org.afritechinnovations.model.finance.Payment;
import org.afritechinnovations.model.finance.PaymentMethod;
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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
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
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class OwnerFinanceServiceTest {

    private static final Long OWNER_ID = 10L;
    private static final LocalDate DUE = LocalDate.of(2026, 10, 15);

    @Mock SchoolRepository schoolRepository;
    @Mock FeeTypeRepository feeTypeRepository;
    @Mock InvoiceRepository invoiceRepository;
    @Mock PaymentRepository paymentRepository;
    @Mock LevelRepository levelRepository;
    @Mock SchoolClassRepository schoolClassRepository;
    @Mock AcademicYearRepository academicYearRepository;
    @Mock StudentEnrollmentRepository studentEnrollmentRepository;
    @Mock ParentStudentRepository parentStudentRepository;
    @Mock EmailService emailService;
    @InjectMocks OwnerFinanceService service;

    private School school;
    private FeeType fee;
    private SchoolClass sixA;
    private Student awa;
    private Student issa;

    @BeforeEach
    void setUp() {
        service.setClock(Clock.fixed(LocalDate.of(2026, 10, 1).atStartOfDay().toInstant(ZoneOffset.UTC), ZoneOffset.UTC));
        school = School.builder().id(1L).name("École ABC").owner(User.builder().id(OWNER_ID).build()).build();
        AcademicYear year = AcademicYear.builder().id(2L).build();
        Level sixieme = Level.builder().id(4L).school(school).name("6e").build();
        sixA = SchoolClass.builder().id(3L).name("6e A").school(school).level(sixieme).academicYear(year).build();
        fee = FeeType.builder().id(7L).school(school).name("Scolarité").amount(new BigDecimal("50000")).build();
        awa = student(20L, "Awa");
        issa = student(21L, "Issa");

        when(schoolRepository.findById(1L)).thenReturn(Optional.of(school));
        when(feeTypeRepository.findById(7L)).thenReturn(Optional.of(fee));
        when(academicYearRepository.findBySchoolIdAndIsCurrentTrue(1L)).thenReturn(Optional.of(year));
        when(schoolClassRepository.findAllWithLevelBySchoolAndYear(1L, 2L)).thenReturn(List.of(sixA));
        when(studentEnrollmentRepository.findBySchoolClassIdAndStatus(3L, EnrollmentStatus.ACTIVE))
                .thenReturn(List.of(enrollment(awa), enrollment(issa)));
        when(studentEnrollmentRepository.findActiveBySchoolId(1L)).thenReturn(List.of(enrollment(awa), enrollment(issa)));
    }

    @Test
    void bulkInvoiceSkipsStudentsAlreadyBilledForTheSameDueDate() {
        when(invoiceRepository.existsActiveInvoice(21L, 7L, DUE)).thenReturn(true);

        OwnerFinanceDto.BulkInvoiceResult result = service.bulkInvoice(1L,
                new OwnerFinanceDto.BulkInvoiceRequest(7L, null, null, DUE, null), OWNER_ID, false);

        assertEquals(1, result.created());
        assertEquals(1, result.skipped());
        assertEquals(2, result.targetedStudents());
        verify(invoiceRepository, times(1)).save(any(Invoice.class));
    }

    @Test
    void bulkInvoiceRejectsArchivedFeeAndForeignOwner() {
        assertThrows(AccessDeniedException.class, () -> service.bulkInvoice(1L,
                new OwnerFinanceDto.BulkInvoiceRequest(7L, null, null, DUE, null), 99L, false));
        fee.setActive(false);
        assertThrows(IllegalArgumentException.class, () -> service.bulkInvoice(1L,
                new OwnerFinanceDto.BulkInvoiceRequest(7L, null, null, DUE, null), OWNER_ID, false));
        verify(invoiceRepository, never()).save(any());
    }

    @Test
    void discountReducesBalanceAndFullExemptionMarksInvoicePaid() {
        Invoice invoice = invoice(30L, awa, DUE);
        when(invoiceRepository.findById(30L)).thenReturn(Optional.of(invoice));
        when(paymentRepository.findByInvoiceId(30L)).thenReturn(List.of(payment(invoice, "20000")));

        OwnerFinanceDto.InvoiceRow row = service.applyDiscount(30L,
                new OwnerFinanceDto.DiscountRequest(new BigDecimal("10000"), "Fratrie"), OWNER_ID, false);
        assertEquals(0, new BigDecimal("40000").compareTo(row.netAmount()));
        assertEquals(0, new BigDecimal("20000").compareTo(row.balance()));
        assertEquals("PENDING", row.status());

        row = service.applyDiscount(30L, new OwnerFinanceDto.DiscountRequest(new BigDecimal("30000"), "Exonération"),
                OWNER_ID, false);
        assertEquals("PAID", row.status());
        assertEquals(0, BigDecimal.ZERO.compareTo(row.balance()));

        assertThrows(IllegalArgumentException.class, () -> service.applyDiscount(30L,
                new OwnerFinanceDto.DiscountRequest(new BigDecimal("40000"), "Trop"), OWNER_ID, false));
        assertThrows(IllegalArgumentException.class, () -> service.applyDiscount(30L,
                new OwnerFinanceDto.DiscountRequest(new BigDecimal("5000"), " "), OWNER_ID, false));
    }

    @Test
    void overviewComputesTotalsOverdueAndCollectionRate() {
        Invoice paidLate = invoice(30L, awa, LocalDate.of(2026, 9, 10));
        Invoice overdue = invoice(31L, issa, LocalDate.of(2026, 9, 10));
        Payment p1 = payment(paidLate, "50000");
        Payment p2 = payment(overdue, "10000");
        when(invoiceRepository.findAllWithDetailsBySchoolId(1L)).thenReturn(List.of(paidLate, overdue));
        when(paymentRepository.findAllWithDetailsBySchoolId(1L)).thenReturn(List.of(p1, p2));

        OwnerFinanceDto.Overview overview = service.overview(1L, OWNER_ID, false);

        assertEquals(0, new BigDecimal("100000").compareTo(overview.expected()));
        assertEquals(0, new BigDecimal("60000").compareTo(overview.collected()));
        assertEquals(0, new BigDecimal("40000").compareTo(overview.remaining()));
        assertEquals(0, new BigDecimal("40000").compareTo(overview.overdueAmount()));
        assertEquals(60.0, overview.collectionRate());
        assertEquals(1, overview.overdueCount());
        assertEquals(1, overview.paidCount());
        assertEquals(InvoiceStatus.OVERDUE, overdue.getStatus());
        assertEquals(1, overview.perClass().size());
        assertEquals(2, overview.perClass().get(0).students());
    }

    @Test
    void reminderFallsBackToStudentEmail() {
        Invoice invoice = invoice(30L, awa, DUE);
        when(invoiceRepository.findById(30L)).thenReturn(Optional.of(invoice));
        when(paymentRepository.findByInvoiceId(30L)).thenReturn(List.of());
        when(parentStudentRepository.findByStudentIdWithParentUser(20L)).thenReturn(List.of());

        assertEquals(1, service.sendReminder(30L, OWNER_ID, false).recipients());
        verify(emailService).sendText(eq("awa@ecole.bf"), any(), any());
    }

    private Student student(Long id, String firstName) {
        return Student.builder().id(id).school(school).registrationNumber("M-" + id)
                .user(User.builder().id(id + 100).firstName(firstName).lastName("Kaboré")
                        .email(firstName.toLowerCase() + "@ecole.bf").build())
                .build();
    }

    private StudentEnrollment enrollment(Student student) {
        return StudentEnrollment.builder().student(student).schoolClass(sixA)
                .enrollmentDate(LocalDate.of(2026, 9, 1)).build();
    }

    private Invoice invoice(Long id, Student student, LocalDate dueDate) {
        return Invoice.builder().id(id).student(student).feeType(fee).amountDue(new BigDecimal("50000"))
                .dueDate(dueDate).status(InvoiceStatus.PENDING).build();
    }

    private Payment payment(Invoice invoice, String amount) {
        return Payment.builder().id(invoice.getId() + 500).invoice(invoice).amount(new BigDecimal(amount))
                .paymentDate(LocalDate.of(2026, 9, 20)).method(PaymentMethod.CASH).reference("2026-0001").build();
    }
}
