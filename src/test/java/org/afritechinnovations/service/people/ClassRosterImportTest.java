package org.afritechinnovations.service.people;

import org.afritechinnovations.dto.people.ImportRosterStudentRequest;
import org.afritechinnovations.model.academic.*;
import org.afritechinnovations.model.common.*;
import org.afritechinnovations.model.finance.*;
import org.afritechinnovations.model.people.*;
import org.afritechinnovations.repository.academic.*;
import org.afritechinnovations.repository.finance.*;
import org.afritechinnovations.repository.people.*;
import org.afritechinnovations.security.SchoolPermissions;
import org.afritechinnovations.service.auth.EmailVerificationService;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.security.access.AccessDeniedException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness=Strictness.LENIENT)
class ClassRosterImportTest {
    @Mock SchoolClassRepository schoolClassRepository;
    @Mock StudentRepository studentRepository;
    @Mock StudentEnrollmentRepository studentEnrollmentRepository;
    @Mock FeeTypeRepository feeTypeRepository;
    @Mock InvoiceRepository invoiceRepository;
    @Mock PaymentRepository paymentRepository;
    @Mock ParentStudentRepository parentStudentRepository;
    @Mock ClassSubjectTeacherRepository classSubjectTeacherRepository;
    @Mock SchoolPermissions permissions;
    @Mock EmailVerificationService invitations;
    @Mock org.afritechinnovations.repository.common.UserRepository userRepository;
    @Mock org.afritechinnovations.repository.common.RoleRepository roleRepository;
    @Mock org.afritechinnovations.repository.common.SchoolUserRepository schoolUserRepository;
    @Mock ParentRepository parentRepository;
    @Mock org.springframework.security.crypto.password.PasswordEncoder passwordEncoder;
    @InjectMocks ClassRosterService service;
    School school; SchoolClass schoolClass; Student student; Invoice invoice;
    List<Payment> payments;
    List<ParentStudent> parents;

    @BeforeEach void setup() {
        school = School.builder().id(1L).owner(User.builder().id(10L).build()).build();
        var year = AcademicYear.builder().id(2L).school(school).build();
        schoolClass = SchoolClass.builder().id(3L).school(school).academicYear(year).build();
        student = Student.builder().id(4L).school(school).registrationNumber("M001")
                .user(User.builder().id(5L).firstName("Awa").lastName("Traoré").email("awa@example.test").build()).build();
        var fee = FeeType.builder().id(6L).school(school).name("Scolarité").active(true).build();
        invoice = Invoice.builder().id(7L).student(student).academicYear(year).feeType(fee)
                .amountDue(new BigDecimal("50000")).dueDate(LocalDate.of(2026, 10, 1)).build();
        payments = new ArrayList<>();
        parents = new ArrayList<>();
        when(parentStudentRepository.findByStudentIdWithParentUser(4L)).thenAnswer(i -> List.copyOf(parents));
        when(roleRepository.findByName("PARENT")).thenReturn(Optional.of(Role.builder().name("PARENT").build()));
        when(passwordEncoder.encode(anyString())).thenReturn("hash");
        when(userRepository.save(any())).thenAnswer(i -> { User value = i.getArgument(0); value.setId(60L); return value; });
        when(parentRepository.save(any())).thenAnswer(i -> { Parent value = i.getArgument(0); value.setId(70L); return value; });
        when(parentStudentRepository.save(any())).thenAnswer(i -> {
            ParentStudent value = i.getArgument(0); if (!parents.contains(value)) parents.add(value); return value;
        });
        when(schoolClassRepository.findById(3L)).thenReturn(Optional.of(schoolClass));
        when(studentRepository.findBySchoolIdAndRegistrationNumberForUpdate(1L,"M001")).thenReturn(Optional.of(student));
        when(studentRepository.findById(4L)).thenReturn(Optional.of(student));
        when(studentEnrollmentRepository.findByStudentIdAndAcademicYearId(4L,2L)).thenReturn(List.of(
                StudentEnrollment.builder().student(student).schoolClass(schoolClass).status(EnrollmentStatus.ACTIVE).build()));
        when(feeTypeRepository.findBySchoolId(1L)).thenReturn(List.of(fee));
        when(invoiceRepository.findByStudentId(4L)).thenReturn(List.of(invoice));
        when(invoiceRepository.findById(7L)).thenReturn(Optional.of(invoice));
        when(invoiceRepository.save(any())).thenAnswer(i -> i.getArgument(0));
        when(paymentRepository.findByInvoiceId(7L)).thenAnswer(i -> List.copyOf(payments));
        when(paymentRepository.save(any())).thenAnswer(i -> { Payment p = i.getArgument(0); p.setId(100L + payments.size()); payments.add(p); return p; });
    }
    ImportRosterStudentRequest request(String fees, String paid) {
        var r = new ImportRosterStudentRequest(); r.setFirstName("Awa"); r.setLastName("Traoré");
        r.setEmail("awa@example.test"); r.setRegistrationNumber("M001"); r.setFeeTypeName("Scolarité");
        r.setFees(new BigDecimal(fees)); r.setPaid(paid == null ? null : new BigDecimal(paid));
        r.setDueDate(invoice.getDueDate()); r.setPaymentDate(LocalDate.now()); r.setPaymentMethod(PaymentMethod.CASH); return r;
    }
    @Test void importsOnlyMissingPaymentAndRetryDoesNotDuplicate() {
        payments.add(Payment.builder().id(99L).invoice(invoice).amount(new BigDecimal("20000")).method(PaymentMethod.CASH).build());
        var result = service.importStudent(3L,request("50000","30000"),10L,false);
        service.importStudent(3L,request("50000","30000"),10L,false);
        assertEquals(2,payments.size());
        assertEquals(0,new BigDecimal("10000").compareTo(payments.getLast().getAmount()));
        assertNotNull(payments.getLast().getReference());
        assertEquals(0,new BigDecimal("20000").compareTo(result.billingSummary().totalBalance()));
        verify(studentRepository,never()).save(any());
    }
    @Test void blankPaidPreservesExistingPaymentsAndUpdatesFees() {
        payments.add(Payment.builder().amount(new BigDecimal("20000")).method(PaymentMethod.CASH).build());
        service.importStudent(3L,request("60000",null),10L,false);
        assertEquals(0,new BigDecimal("60000").compareTo(invoice.getAmountDue()));
        verify(paymentRepository,never()).save(any());
    }
    @Test void createsMissingInvoiceForAnAlreadyImportedStudent() {
        var stored = new ArrayList<Invoice>();
        when(invoiceRepository.findByStudentId(4L)).thenAnswer(i -> List.copyOf(stored));
        when(invoiceRepository.save(any())).thenAnswer(i -> {
            Invoice value = i.getArgument(0);
            if (value.getId() == null) { value.setId(7L); stored.add(value); }
            return value;
        });
        service.importStudent(3L,request("50000","0"),10L,false);
        service.importStudent(3L,request("50000","0"),10L,false);
        assertEquals(1,stored.size());
        assertEquals(2L,stored.getFirst().getAcademicYear().getId());
        assertEquals(0,new BigDecimal("50000").compareTo(stored.getFirst().getAmountDue()));
        verify(studentRepository,never()).save(any());
        verify(paymentRepository,never()).save(any());
    }
    @Test void rejectsDecreasingPaidTotal() {
        payments.add(Payment.builder().amount(new BigDecimal("30000")).build());
        assertThrows(IllegalArgumentException.class,()->service.importStudent(3L,request("50000","20000"),10L,false));
        verify(paymentRepository,never()).save(any());
    }
    @Test void rejectsOverpayment() {
        assertThrows(IllegalArgumentException.class,()->service.importStudent(3L,request("50000","60000"),10L,false));
        verify(invoiceRepository,never()).save(any());
    }
    @Test void rejectsAnotherIdentityUsingSameRegistrationNumber() {
        var r=request("50000","0"); r.setEmail("other@example.test");
        assertThrows(IllegalArgumentException.class,()->service.importStudent(3L,r,10L,false));
    }
    @Test void omittedEmailKeepsExistingEmailAndStillMatchesByRegistrationNumber() {
        var r=request("50000","0"); r.setEmail(null);
        service.importStudent(3L,r,10L,false);
        assertEquals("awa@example.test", student.getUser().getEmail());
        r.setEmail("");
        service.importStudent(3L,r,10L,false);
        assertEquals("awa@example.test", student.getUser().getEmail());
    }
    @Test void studentWithoutEmailCanBeReimported() {
        student.getUser().setEmail(null);
        var r=request("50000","0"); r.setEmail(null);
        service.importStudent(3L,r,10L,false);
        assertNull(student.getUser().getEmail());
        verify(studentRepository,never()).save(any());
    }
    @Test void requiresFinancePermissionInAdditionToStudents() {
        when(permissions.staffAllows(1L,11L,StaffModule.STUDENTS)).thenReturn(true);
        assertThrows(AccessDeniedException.class,()->service.importStudent(3L,request("50000","0"),11L,false));
    }
    org.afritechinnovations.dto.people.CreateRosterParentRequest parentRequest() {
        var p = new org.afritechinnovations.dto.people.CreateRosterParentRequest();
        p.setFirstName("Mamadou"); p.setLastName("Traoré"); p.setPhone("+22670000000"); p.setRelationship("Père"); return p;
    }
    @Test void importsParentWithoutEmailAndRetryKeepsOneParentLink() {
        var r=request("50000","0"); r.setParent(parentRequest());
        var result=service.importStudent(3L,r,10L,false);
        service.importStudent(3L,r,10L,false);
        assertEquals(1,parents.size());
        assertEquals("+22670000000",result.parents().getFirst().phone());
        assertEquals("Père",result.parents().getFirst().relationship());
        assertNull(result.parents().getFirst().email());
        verify(parentRepository,times(1)).save(any());
    }
    @Test void importsParentIntoAnExistingStudentWithoutReimportingFinancialAmounts() {
        var r=request("50000",null); r.setFees(null); r.setFeeTypeName(null); r.setDueDate(null); r.setParent(parentRequest());
        service.importStudent(3L,r,10L,false);
        assertEquals(1,parents.size());
        verify(invoiceRepository,never()).save(any());
        verify(studentRepository,never()).save(any());
    }
    @Test void reusesParentWithEmailAndPreservesExistingSharedIdentity() {
        User existing=User.builder().id(80L).firstName("Mamadou").lastName("Traoré").active(true).approved(true).emailVerified(true).passwordSet(true).email("parent@example.test").phone("+22671000000").build();
        Parent parent=Parent.builder().id(90L).user(existing).build();
        when(userRepository.findByEmailIgnoreCase("parent@example.test")).thenReturn(Optional.of(existing));
        when(parentRepository.findByUserId(80L)).thenReturn(Optional.of(parent));
        var r=request("50000","0"); var p=parentRequest(); p.setEmail("parent@example.test"); r.setParent(p);
        var result = service.importStudent(3L,r,10L,false);
        service.importStudent(3L,r,10L,false);
        assertEquals(1,parents.size());
        assertTrue(result.parents().getFirst().emailVerified());
        assertTrue(result.parents().getFirst().passwordSet());
        assertSame(parent,parents.getFirst().getParent());
        assertEquals("+22671000000",existing.getPhone());
        verify(parentRepository,never()).save(any());
        verify(userRepository,never()).save(any());
    }
    @Test void refusesClosedYearAndCancelledOrDiscountedInvoices() {
        schoolClass.getAcademicYear().setClosedAt(java.time.LocalDateTime.now());
        assertThrows(IllegalArgumentException.class,()->service.importStudent(3L,request("50000","0"),10L,false));
        schoolClass.getAcademicYear().setClosedAt(null); invoice.setStatus(InvoiceStatus.CANCELLED);
        assertThrows(IllegalArgumentException.class,()->service.importStudent(3L,request("50000","0"),10L,false));
        invoice.setStatus(InvoiceStatus.PENDING); invoice.setDiscountAmount(BigDecimal.TEN);
        assertThrows(IllegalArgumentException.class,()->service.importStudent(3L,request("50000","0"),10L,false));
    }
}
