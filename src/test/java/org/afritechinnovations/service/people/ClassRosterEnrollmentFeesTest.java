package org.afritechinnovations.service.people;

import org.afritechinnovations.dto.people.NewStudentEnrollmentRequest;
import org.afritechinnovations.dto.people.OwnerEnrollmentDto;
import org.afritechinnovations.model.academic.AcademicYear;
import org.afritechinnovations.model.academic.Level;
import org.afritechinnovations.model.academic.SchoolClass;
import org.afritechinnovations.model.common.Role;
import org.afritechinnovations.model.common.School;
import org.afritechinnovations.model.common.User;
import org.afritechinnovations.model.finance.FeeType;
import org.afritechinnovations.model.finance.Invoice;
import org.afritechinnovations.model.finance.Payment;
import org.afritechinnovations.model.finance.PaymentMethod;
import org.afritechinnovations.model.people.Student;
import org.afritechinnovations.repository.academic.ClassSubjectTeacherRepository;
import org.afritechinnovations.repository.common.RoleRepository;
import org.afritechinnovations.repository.common.SchoolUserRepository;
import org.afritechinnovations.repository.common.UserRepository;
import org.afritechinnovations.repository.finance.FeeTypeRepository;
import org.afritechinnovations.repository.finance.InvoiceRepository;
import org.afritechinnovations.repository.finance.PaymentRepository;
import org.afritechinnovations.repository.people.ParentStudentRepository;
import org.afritechinnovations.repository.people.StudentEnrollmentRepository;
import org.afritechinnovations.repository.people.StudentRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ClassRosterEnrollmentFeesTest {

    @Mock UserRepository userRepository;
    @Mock StudentRepository studentRepository;
    @Mock RoleRepository roleRepository;
    @Mock SchoolUserRepository schoolUserRepository;
    @Mock PasswordEncoder passwordEncoder;
    @Mock StudentEnrollmentRepository studentEnrollmentRepository;
    @Mock ParentStudentRepository parentStudentRepository;
    @Mock ClassSubjectTeacherRepository classSubjectTeacherRepository;
    @Mock FeeTypeRepository feeTypeRepository;
    @Mock InvoiceRepository invoiceRepository;
    @Mock PaymentRepository paymentRepository;
    @Mock org.afritechinnovations.repository.people.ParentRepository parentRepository;
    @Mock org.afritechinnovations.service.auth.EmailVerificationService invitations;
    @InjectMocks ClassRosterService service;

    private final School school = School.builder().id(1L).name("École ABC").build();
    private final Level cp1 = Level.builder().id(1L).name("CP1").build();
    private final AcademicYear year = AcademicYear.builder().id(5L).label("2026-2027")
            .endDate(LocalDate.now().plusMonths(6)).build();
    private final SchoolClass cp1a = SchoolClass.builder().id(11L).name("CP1-A").school(school).level(cp1)
            .academicYear(year).build();
    private final FeeType registration = FeeType.builder().id(31L).school(school).name("Inscription")
            .amount(new BigDecimal("10000")).active(true).build();
    private final FeeType tuition = FeeType.builder().id(32L).school(school).name("Scolarité")
            .amount(new BigDecimal("50000")).level(cp1).active(true).build();

    @BeforeEach
    void setUp() {
        when(userRepository.save(any(User.class))).thenAnswer(i -> i.getArgument(0));
        when(passwordEncoder.encode(anyString())).thenReturn("hash");
        when(roleRepository.findByName("STUDENT")).thenReturn(Optional.of(Role.builder().name("STUDENT").build()));
        when(studentRepository.findBySchoolIdAndRegistrationNumber(any(), any())).thenReturn(Optional.empty());
        when(studentRepository.save(any(Student.class))).thenAnswer(i -> {
            Student s = i.getArgument(0);
            s.setId(300L);
            return s;
        });
        when(feeTypeRepository.findById(31L)).thenReturn(Optional.of(registration));
        when(feeTypeRepository.findById(32L)).thenReturn(Optional.of(tuition));
        when(invoiceRepository.save(any(Invoice.class))).thenAnswer(i -> i.getArgument(0));
        when(paymentRepository.save(any(Payment.class))).thenAnswer(i -> i.getArgument(0));
        when(paymentRepository.countByReferenceStartingWith(anyString())).thenReturn(0L, 1L);
        when(parentStudentRepository.findByStudentIdWithParentUser(any())).thenReturn(List.of());
        when(classSubjectTeacherRepository.findAllWithTeacherByClassId(any())).thenReturn(List.of());
    }

    private NewStudentEnrollmentRequest request(PaymentMethod method, OwnerEnrollmentDto.FeeLine... lines) {
        var r = new NewStudentEnrollmentRequest();
        r.setClassId(11L);
        r.setFirstName("Sali");
        r.setLastName("Ouédraogo");
        r.setEmail("sali@ecole.bf");
        r.setPassword("motdepasse");
        r.setRegistrationNumber("M-300");
        r.setFees(List.of(lines));
        r.setPaymentMethod(method);
        return r;
    }

    @Test
    void billsFeesAndRecordsPaymentsWithReceiptReferences() {
        var result = service.enrollNewStudentWithFees(cp1a, request(PaymentMethod.CASH,
                new OwnerEnrollmentDto.FeeLine(31L, new BigDecimal("10000")),
                new OwnerEnrollmentDto.FeeLine(32L, new BigDecimal("20000"))));

        assertEquals("École ABC", result.schoolName());
        assertEquals("CP1-A", result.className());
        assertEquals("2026-2027", result.yearLabel());
        assertEquals(2, result.invoices().size());
        var first = result.invoices().get(0);
        assertEquals("PAID", first.status());
        assertEquals(1, first.payments().size());
        assertNotNull(first.payments().get(0).reference());
        var second = result.invoices().get(1);
        assertEquals("PENDING", second.status());
        assertEquals(0, new BigDecimal("30000").compareTo(second.balance()));
        assertNotEquals(first.payments().get(0).reference(), second.payments().get(0).reference());
        verify(paymentRepository, times(2)).save(any(Payment.class));
    }

    @Test
    void billsWithoutPaymentWhenNothingIsPaid() {
        var result = service.enrollNewStudentWithFees(cp1a, request(null,
                new OwnerEnrollmentDto.FeeLine(31L, BigDecimal.ZERO)));

        assertEquals(1, result.invoices().size());
        assertTrue(result.invoices().get(0).payments().isEmpty());
        verify(paymentRepository, never()).save(any());
    }

    @Test
    void generatesIncrementalRegistrationNumberWhenLeftBlank() {
        AcademicYear y = AcademicYear.builder().id(5L).label("2026-2027").startDate(LocalDate.of(2026, 10, 1)).build();
        SchoolClass cls = SchoolClass.builder().id(11L).name("CP1-A").school(school).level(cp1).academicYear(y).build();
        when(studentRepository.findRegistrationNumbersBySchoolAndPrefix(1L, "MAT-2026-"))
                .thenReturn(List.of("MAT-2026-001", "MAT-2026-009", "MAT-2026-ABC"));
        var r = request(null);
        r.setRegistrationNumber("  ");

        var result = service.enrollNewStudentWithFees(cls, r);

        assertEquals("MAT-2026-010", result.student().registrationNumber());
        assertEquals("MAT-2027-001", service.nextRegistrationNumber(SchoolClass.builder().school(school)
                .academicYear(AcademicYear.builder().startDate(LocalDate.of(2027, 9, 1)).build()).build()));
    }

    @Test
    void createsGuardianWithoutEmailAndReusesExistingParentByEmail() {
        when(roleRepository.findByName("PARENT")).thenReturn(Optional.of(Role.builder().name("PARENT").build()));
        when(parentRepository.save(any(org.afritechinnovations.model.people.Parent.class))).thenAnswer(i -> {
            var p = (org.afritechinnovations.model.people.Parent) i.getArgument(0);
            p.setId(70L);
            return p;
        });
        User existing = User.builder().id(80L).firstName("Awa").lastName("Kaboré").email("awa@ecole.bf").build();
        var existingParent = org.afritechinnovations.model.people.Parent.builder().id(81L).user(existing).build();
        when(userRepository.findByEmailIgnoreCase("awa@ecole.bf")).thenReturn(Optional.of(existing));
        when(parentRepository.findByUserId(80L)).thenReturn(Optional.of(existingParent));
        when(schoolUserRepository.findByUserId(any())).thenReturn(List.of());
        var r = request(null);
        r.setGuardians(List.of(
                new OwnerEnrollmentDto.Guardian(null, "Issa", "Ouédraogo", "", "", "Père"),
                new OwnerEnrollmentDto.Guardian(null, "Awa", "Kaboré", " AWA@ecole.bf ", "70000000", "Mère")));

        service.enrollNewStudentWithFees(cp1a, r);

        var users = org.mockito.ArgumentCaptor.forClass(User.class);
        verify(userRepository, atLeast(2)).save(users.capture());
        User guardian = users.getAllValues().stream().filter(u -> "Issa".equals(u.getFirstName())).findFirst().orElseThrow();
        assertNull(guardian.getEmail());
        assertNull(guardian.getPhone());
        assertEquals("70000000", existing.getPhone());
        var links = org.mockito.ArgumentCaptor.forClass(org.afritechinnovations.model.people.ParentStudent.class);
        verify(parentStudentRepository, times(2)).save(links.capture());
        assertEquals("Père", links.getAllValues().get(0).getRelationship());
        assertSame(existingParent, links.getAllValues().get(1).getParent());
        verify(schoolUserRepository, times(3)).save(any());
    }

    @Test
    void attachesSelectedExistingParentWithoutChangingItsAccount() {
        when(roleRepository.findByName("PARENT")).thenReturn(Optional.of(Role.builder().name("PARENT").build()));
        User known = User.builder().id(90L).firstName("Moussa").lastName("Kaboré").email("moussa@ecole.bf")
                .phone("70111111").build();
        var knownParent = org.afritechinnovations.model.people.Parent.builder().id(91L).user(known).build();
        when(parentRepository.findById(91L)).thenReturn(Optional.of(knownParent));
        when(schoolUserRepository.findByUserId(90L)).thenReturn(List.of(org.afritechinnovations.model.common.SchoolUser
                .builder().user(known).school(school).role(Role.builder().name("PARENT").build()).build()));
        var r = request(null);
        r.setGuardians(List.of(new OwnerEnrollmentDto.Guardian(91L, "Moussa", "Kaboré", "autre@x.bf", "70999999", "Père")));

        service.enrollNewStudentWithFees(cp1a, r);

        var link = org.mockito.ArgumentCaptor.forClass(org.afritechinnovations.model.people.ParentStudent.class);
        verify(parentStudentRepository).save(link.capture());
        assertSame(knownParent, link.getValue().getParent());
        assertEquals("Père", link.getValue().getRelationship());
        assertEquals("moussa@ecole.bf", known.getEmail());
        assertEquals("70111111", known.getPhone());
        verify(userRepository, never()).save(known);
        verify(parentRepository, never()).save(any());
        verify(schoolUserRepository, times(1)).save(any());
    }

    @Test
    void refusesInvalidFeesBeforeCreatingTheStudent() {
        assertThrows(IllegalArgumentException.class, () -> service.enrollNewStudentWithFees(cp1a,
                request(PaymentMethod.CASH, new OwnerEnrollmentDto.FeeLine(31L, new BigDecimal("15000")))));
        assertThrows(IllegalArgumentException.class, () -> service.enrollNewStudentWithFees(cp1a,
                request(null, new OwnerEnrollmentDto.FeeLine(31L, new BigDecimal("5000")))));
        assertThrows(IllegalArgumentException.class, () -> service.enrollNewStudentWithFees(cp1a,
                request(PaymentMethod.CASH, new OwnerEnrollmentDto.FeeLine(31L, null),
                        new OwnerEnrollmentDto.FeeLine(31L, null))));

        tuition.setLevel(Level.builder().id(2L).build());
        assertThrows(IllegalArgumentException.class, () -> service.enrollNewStudentWithFees(cp1a,
                request(PaymentMethod.CASH, new OwnerEnrollmentDto.FeeLine(32L, null))));
        verify(userRepository, never()).save(any());
    }

    @Test
    void studentAndGuardianWithEmailReceiveInvitationsWithoutAdministratorChosenPasswords() {
        when(roleRepository.findByName("PARENT")).thenReturn(Optional.of(Role.builder().name("PARENT").build()));
        when(parentRepository.save(any())).thenAnswer(i -> i.getArgument(0));
        var r = request(null);
        r.setPassword("AdminChosenPassword");
        r.setGuardians(List.of(new OwnerEnrollmentDto.Guardian(null, "Awa", "Diallo", "awa@test.bf", null, "Mère")));
        service.enrollNewStudentWithFees(cp1a, r);
        var accounts = org.mockito.ArgumentCaptor.forClass(User.class);
        verify(userRepository, times(2)).save(accounts.capture());
        assertTrue(accounts.getAllValues().stream().allMatch(u -> !u.getPasswordSet() && !u.getEmailVerified()));
        verify(invitations, times(2)).sendInvitation(any());
        verify(passwordEncoder, never()).encode("AdminChosenPassword");
    }

    @Test void schoolCanCreateDossierForPendingStudentWithoutChangingChosenPasswordOrApprovingAccount() {
        User pending = User.builder().id(7L).email("sali@ecole.bf").firstName("Sali").lastName("Diallo")
                .approved(false).requestedRole(org.afritechinnovations.model.common.RoleName.STUDENT).requestedSchoolId(1L)
                .passwordHash("chosen").emailVerified(true).build();
        when(userRepository.findByEmailIgnoreCase("sali@ecole.bf")).thenReturn(Optional.of(pending));
        when(userRepository.existsByEmailIgnoreCase("sali@ecole.bf")).thenReturn(true);
        service.enrollNewStudentWithFees(cp1a, request(PaymentMethod.CASH));
        verify(studentRepository).save(org.mockito.ArgumentMatchers.argThat(s -> s.getUser() == pending && s.getRegistrationNumber().equals("M-300")));
        assertFalse(pending.getApproved()); assertEquals("chosen", pending.getPasswordHash());
        verify(passwordEncoder, never()).encode(anyString());
    }

}
