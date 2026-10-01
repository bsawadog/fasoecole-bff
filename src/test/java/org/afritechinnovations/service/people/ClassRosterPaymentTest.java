package org.afritechinnovations.service.people;

import org.afritechinnovations.dto.people.CreateStudentPaymentRequest;
import org.afritechinnovations.model.finance.FeeType;
import org.afritechinnovations.model.finance.Invoice;
import org.afritechinnovations.model.finance.InvoiceStatus;
import org.afritechinnovations.model.finance.Payment;
import org.afritechinnovations.model.finance.PaymentMethod;
import org.afritechinnovations.model.common.School;
import org.afritechinnovations.model.common.User;
import org.afritechinnovations.model.people.Student;
import org.afritechinnovations.repository.finance.InvoiceRepository;
import org.afritechinnovations.repository.finance.PaymentRepository;
import org.afritechinnovations.repository.people.StudentRepository;
import org.afritechinnovations.repository.people.StudentEnrollmentRepository;
import org.afritechinnovations.repository.people.ParentStudentRepository;
import org.afritechinnovations.repository.academic.GradeRepository;
import org.afritechinnovations.repository.academic.AttendanceRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.ArgumentCaptor;
import org.springframework.security.access.AccessDeniedException;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ClassRosterPaymentTest {

    @Mock StudentRepository studentRepository;
    @Mock InvoiceRepository invoiceRepository;
    @Mock PaymentRepository paymentRepository;
    @Mock StudentEnrollmentRepository studentEnrollmentRepository;
    @Mock ParentStudentRepository parentStudentRepository;
    @Mock GradeRepository gradeRepository;
    @Mock AttendanceRepository attendanceRepository;
    @InjectMocks ClassRosterService service;

    private Invoice invoice;
    private Payment payment;

    @BeforeEach
    void setUp() {
        invoice = Invoice.builder().id(2L).student(Student.builder().id(1L).build())
                .feeType(FeeType.builder().id(3L).name("Scolarité").build())
                .amountDue(new BigDecimal("100.00")).dueDate(LocalDate.now().plusDays(2))
                .status(InvoiceStatus.PAID).build();
        payment = Payment.builder().id(4L).invoice(invoice).amount(new BigDecimal("100.00"))
                .paymentDate(LocalDate.now()).method(PaymentMethod.CASH).reference("2026-0001").build();
    }

    @Test
    void editingPaymentReopensInvoiceAndKeepsReference() {
        stubInvoiceAndPayment();
        when(paymentRepository.findByInvoiceId(2L)).thenReturn(List.of(payment));
        CreateStudentPaymentRequest request = request(new BigDecimal("40.00"));

        var result = service.updatePayment(1L, 2L, 4L, request, 10L, true);

        assertEquals(InvoiceStatus.PENDING, invoice.getStatus());
        assertEquals(new BigDecimal("60.00"), result.balance());
        assertEquals("2026-0001", result.payments().get(0).reference());
        verify(paymentRepository).save(payment);
        verify(invoiceRepository).save(invoice);
    }

    @Test
    void deletingPaymentReopensInvoice() {
        stubInvoiceAndPayment();
        when(paymentRepository.findByInvoiceId(2L)).thenReturn(List.of());

        service.deletePayment(1L, 2L, 4L, 10L, true);

        verify(paymentRepository).delete(payment);
        assertEquals(InvoiceStatus.PENDING, invoice.getStatus());
        verify(invoiceRepository).save(invoice);
    }

    @Test
    void editingPaymentToFullAmountMarksInvoicePaid() {
        stubInvoiceAndPayment();
        invoice.setStatus(InvoiceStatus.PENDING);
        payment.setAmount(new BigDecimal("40.00"));
        when(paymentRepository.findByInvoiceId(2L)).thenReturn(List.of(payment));

        service.updatePayment(1L, 2L, 4L, request(new BigDecimal("100.00")), 10L, true);

        assertEquals(InvoiceStatus.PAID, invoice.getStatus());
        verify(invoiceRepository).save(invoice);
    }

    @Test
    void deletingPaymentOnPastDueInvoiceMarksItOverdue() {
        stubInvoiceAndPayment();
        invoice.setDueDate(LocalDate.now().minusDays(1));
        when(paymentRepository.findByInvoiceId(2L)).thenReturn(List.of());

        service.deletePayment(1L, 2L, 4L, 10L, true);

        assertEquals(InvoiceStatus.OVERDUE, invoice.getStatus());
    }

    @Test
    void deniesOtherSchoolOwnerBeforeFetchingInvoice() {
        Student student = Student.builder().id(1L)
                .school(School.builder().owner(User.builder().id(11L).build()).build()).build();
        when(studentRepository.findById(1L)).thenReturn(Optional.of(student));

        assertThrows(AccessDeniedException.class,
                () -> service.deletePayment(1L, 2L, 4L, 10L, false));
        verifyNoInteractions(invoiceRepository, paymentRepository);
    }

    @Test
    void rejectsPaymentFromAnotherInvoice() {
        stubInvoiceAndPayment();
        payment.setInvoice(Invoice.builder().id(9L).build());

        assertThrows(IllegalArgumentException.class,
                () -> service.deletePayment(1L, 2L, 4L, 10L, true));
        verify(paymentRepository, never()).delete(any());
    }

    @Test
    void rejectsOverpaymentWithoutUpdatingDatabase() {
        stubInvoiceAndPayment();
        var otherPayment = Payment.builder().id(5L).amount(new BigDecimal("70.00")).build();
        when(paymentRepository.findByInvoiceId(2L)).thenReturn(List.of(payment, otherPayment));

        assertThrows(IllegalArgumentException.class,
                () -> service.updatePayment(1L, 2L, 4L, request(new BigDecimal("40.00")), 10L, true));
        verify(paymentRepository, never()).save(any());
    }

    @Test
    void newPaymentSkipsExistingReceiptReferenceAfterDeletion() {
        when(studentRepository.findById(1L)).thenReturn(Optional.of(Student.builder().id(1L).build()));
        when(invoiceRepository.findById(2L)).thenReturn(Optional.of(invoice));
        when(paymentRepository.findByInvoiceId(2L)).thenReturn(List.of());
        String prefix = String.valueOf(LocalDate.now().getYear());
        when(paymentRepository.countByReferenceStartingWith(prefix + "-")).thenReturn(1L);
        when(paymentRepository.existsByReference(prefix + "-0002")).thenReturn(true);

        service.addPayment(1L, 2L, request(new BigDecimal("40.00")), 10L, true);

        ArgumentCaptor<Payment> saved = ArgumentCaptor.forClass(Payment.class);
        verify(paymentRepository).save(saved.capture());
        assertEquals(prefix + "-0003", saved.getValue().getReference());
    }

    @Test
    void cancelledInvoiceHasNoBalanceAndIsExcludedFromAmountDueButKeepsCollectedPayments() {
        Student student = Student.builder().id(1L).user(User.builder().id(8L).build())
                .school(School.builder().id(6L).build()).build();
        Invoice active = Invoice.builder().id(7L).student(student)
                .feeType(FeeType.builder().id(3L).name("Inscription").build())
                .amountDue(new BigDecimal("80.00")).dueDate(LocalDate.now().plusDays(1))
                .status(InvoiceStatus.PENDING).build();
        invoice.setStudent(student);
        invoice.setStatus(InvoiceStatus.CANCELLED);
        payment.setAmount(new BigDecimal("20.00"));
        when(studentRepository.findById(1L)).thenReturn(Optional.of(student));
        when(invoiceRepository.findByStudentId(1L)).thenReturn(List.of(invoice, active));
        when(paymentRepository.findByInvoiceId(2L)).thenReturn(List.of(payment));
        when(paymentRepository.findByInvoiceId(7L)).thenReturn(List.of());

        var detail = service.getStudentDetail(1L, 10L, true);

        assertEquals(new BigDecimal("80.00"), detail.billingSummary().totalDue());
        assertEquals(new BigDecimal("20.00"), detail.billingSummary().totalPaid());
        assertEquals(new BigDecimal("80.00"), detail.billingSummary().totalBalance());
        assertEquals(BigDecimal.ZERO, detail.invoices().stream()
                .filter(item -> item.id().equals(2L)).findFirst().orElseThrow().balance());
    }

    private void stubInvoiceAndPayment() {
        when(studentRepository.findById(1L)).thenReturn(Optional.of(Student.builder().id(1L).build()));
        when(invoiceRepository.findById(2L)).thenReturn(Optional.of(invoice));
        when(paymentRepository.findById(4L)).thenReturn(Optional.of(payment));
    }

    private CreateStudentPaymentRequest request(BigDecimal amount) {
        CreateStudentPaymentRequest request = new CreateStudentPaymentRequest();
        request.setAmount(amount);
        request.setPaymentDate(LocalDate.now());
        request.setMethod(PaymentMethod.CASH);
        return request;
    }
}
