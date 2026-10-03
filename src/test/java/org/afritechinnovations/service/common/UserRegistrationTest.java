package org.afritechinnovations.service.common;

import org.afritechinnovations.dto.auth.RegisterUserRequest;
import org.afritechinnovations.model.common.*;
import org.afritechinnovations.repository.common.*;
import org.afritechinnovations.repository.people.ParentRepository;
import org.afritechinnovations.service.auth.EmailVerificationService;
import org.afritechinnovations.service.people.ClassRosterService;
import org.afritechinnovations.service.people.TeacherProfileService;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.security.crypto.password.PasswordEncoder;
import java.util.Optional;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class UserRegistrationTest {
    @Mock UserRepository users;
    @Mock SchoolRepository schools;
    @Mock SchoolUserRepository memberships;
    @Mock RoleRepository roles;
    @Mock PasswordEncoder encoder;
    @Mock EmailVerificationService mail;
    @Mock TeacherProfileService teachers;
    @Mock ParentRepository parents;
    @Mock ClassRosterService roster;
    @Mock AccountOnboardingService onboarding;
    @Mock ParentChildAdmissionService childAdmission;
    @Mock SchoolIdentityAdmissionService identityAdmission;
    @InjectMocks UserService service;
    School school;
    User pending;

    @BeforeEach
    void setup() {
        school = School.builder().id(2L).owner(User.builder().id(10L).build()).status(SchoolStatus.ACTIVE).build();
        pending = User.builder().id(7L).email("awa@test.bf").firstName("Awa").lastName("Diallo")
                .approved(false).emailVerified(true).requestedSchoolId(2L).build();
        when(schools.findById(2L)).thenReturn(Optional.of(school));
        when(users.findById(7L)).thenReturn(Optional.of(pending));
        when(users.save(any())).thenAnswer(i -> { User u = i.getArgument(0); if (u.getId() == null) u.setId(7L); return u; });
        when(encoder.encode(anyString())).thenReturn("hash");
        when(roles.findByName(anyString())).thenAnswer(i -> Optional.of(Role.builder().name(i.getArgument(0)).build()));
    }

    RegisterUserRequest registration(RoleName role) {
        RegisterUserRequest r = new RegisterUserRequest();
        r.setFirstName(" Awa "); r.setLastName(" Diallo "); r.setEmail(" Awa@Test.bf ");
        r.setPassword("MotDePasseSecret"); r.setSchoolId(2L); r.setRequestedRole(role); r.setSchoolIdentifier(role == RoleName.STUDENT ? "001" : role == RoleName.TEACHER ? "EMP-01" : null);
        if (role == RoleName.PARENT) r.setChildRegistrationNumbers(java.util.List.of("MAT-01"));
        return r;
    }

    @ParameterizedTest @EnumSource(value = RoleName.class, names = {"TEACHER", "PARENT", "STUDENT"})
    void everyPublicProfileStartsUnapprovedUnverifiedAndWithoutSchoolMembership(RoleName role) {
        service.receiveRegistration(registration(role));
        ArgumentCaptor<User> account = ArgumentCaptor.forClass(User.class);
        verify(users).save(account.capture());
        User user = account.getValue();
        assertEquals("awa@test.bf", user.getEmail());
        assertEquals("Awa", user.getFirstName());
        assertFalse(user.getApproved()); assertFalse(user.getEmailVerified()); assertTrue(user.getPasswordSet());
        assertEquals(role, user.getRequestedRole()); assertEquals(2L, user.getRequestedSchoolId());
        verify(mail).sendVerification(user);
        verify(memberships, never()).save(any());
    }

    @ParameterizedTest @EnumSource(value = RoleName.class, names = {"SUPER_ADMIN", "SCHOOL_ADMIN", "STAFF"})
    void privilegedPublicRegistrationNeverCreatesAnAccountOrSendsActivation(RoleName role) {
        assertThrows(IllegalArgumentException.class, () -> service.receiveRegistration(registration(role)));
        assertThrows(IllegalArgumentException.class, () -> service.requestActivationOfSchoolCreatedAccount(registration(role)));
        verify(users, never()).save(any()); verifyNoInteractions(mail);
    }

    @Test
    void inactiveSchoolCannotReceiveRegistration() {
        school.setStatus(SchoolStatus.SUSPENDED);
        assertThrows(IllegalArgumentException.class, () -> service.receiveRegistration(registration(RoleName.PARENT)));
        verify(users, never()).save(any()); verifyNoInteractions(mail);
    }

    @Test
    void registeringSchoolInvitedAccountDoesNotChangeIdentityOrPassword() {
        pending.setPasswordSet(false); pending.setPasswordHash("random");
        when(users.findByEmailIgnoreCase("awa@test.bf")).thenReturn(Optional.of(pending));
        service.receiveRegistration(registration(RoleName.PARENT));
        assertEquals("random", pending.getPasswordHash()); assertEquals("Awa", pending.getFirstName());
        verify(users, never()).save(any()); verify(mail).sendActivation(pending, 2L, RoleName.PARENT, java.util.List.of("MAT-01"), null);
    }

    @Test
    void repeatedRegistrationResendsProofButNeverOverwritesPassword() {
        pending.setEmailVerified(false); pending.setPasswordHash("old");
        when(users.findByEmailIgnoreCase("awa@test.bf")).thenReturn(Optional.of(pending));
        service.receiveRegistration(registration(RoleName.TEACHER));
        verify(mail).sendVerification(pending); assertEquals("old", pending.getPasswordHash());
        verify(users, never()).save(any());
        pending.setEmailVerified(true);
        service.receiveRegistration(registration(RoleName.TEACHER));
        verify(mail, times(1)).sendVerification(pending);
    }

    @ParameterizedTest @EnumSource(value = RoleName.class, names = {"TEACHER", "PARENT", "STUDENT"})
    void approvalProvisionsTheCorrectProfileWithoutChangingPassword(RoleName role) {
        pending.setPasswordHash("chosen"); pending.setRequestedRole(role); pending.setSchoolIdentifier(role == RoleName.STUDENT ? "001" : role == RoleName.TEACHER ? "EMP-01" : null);
        var result = service.approvePendingUser(7L, 2L, role, 10L, false, 11L, null);
        assertTrue(result.getApproved()); assertEquals("chosen", pending.getPasswordHash());
        verify(memberships).save(argThat(m -> m.getUser() == pending && m.getSchool() == school && m.getRole().getName().equals(role.name())));
        if (role == RoleName.TEACHER) verify(identityAdmission).attachApproved(pending, school, role, "EMP-01");
        if (role == RoleName.PARENT) verify(childAdmission).attachApproved(pending, 2L, pending.getChildRegistrationNumbers());
        if (role == RoleName.STUDENT) verify(identityAdmission).attachApproved(pending, school, role, "001");
    }

    @Test
    void unverifiedOrInactiveUserCannotBeApproved() {
        pending.setEmailVerified(false);
        assertThrows(IllegalArgumentException.class, () -> service.approvePendingUser(7L, 2L, RoleName.TEACHER, 10L, false));
        verify(memberships, never()).save(any());
        pending.setEmailVerified(true); pending.setActive(false);
        assertThrows(IllegalArgumentException.class, () -> service.approvePendingUser(7L, 2L, RoleName.TEACHER, 10L, false));
    }

    @Test
    void otherOwnerCannotApproveAndApprovalCannotBeRepeated() {
        assertThrows(org.springframework.security.access.AccessDeniedException.class,
                () -> service.approvePendingUser(7L, 2L, RoleName.PARENT, 99L, false));
        pending.setApproved(true);
        assertThrows(IllegalArgumentException.class, () -> service.approvePendingUser(7L, 2L, RoleName.PARENT, 10L, false));
        verify(memberships, never()).save(any());
    }

    @Test
    void parentMatriculesAreRequiredStoredWithoutLookingUpChildrenBeforeApproval() {
        RegisterUserRequest request = registration(RoleName.PARENT);
        request.setChildRegistrationNumbers(java.util.List.of());
        assertThrows(IllegalArgumentException.class, () -> service.receiveRegistration(request));
        verify(users, never()).save(any());
        request.setChildRegistrationNumbers(java.util.List.of(" 001 ", "002", "001"));
        service.receiveRegistration(request);
        verify(users).save(argThat(u -> u.getChildRegistrationNumbers().equals(java.util.List.of("001", "002"))));
        verifyNoInteractions(childAdmission);
    }

    @Test
    void parentCanCorrectOwnPendingClaimsButNotApprovedAccountClaims() {
        pending.setRequestedRole(RoleName.PARENT);
        var request = new org.afritechinnovations.dto.common.UpdateProfileRequest();
        request.setFirstName("Awa"); request.setLastName("Diallo"); request.setChildRegistrationNumbers(java.util.List.of(" 001 "));
        service.updateProfile(7L, request);
        assertEquals(java.util.List.of("001"), pending.getChildRegistrationNumbers());
        pending.setApproved(true);
        assertThrows(IllegalArgumentException.class, () -> service.updateProfile(7L, request));
    }

    @Test
    void ownerReviewShowsMatchesButSelfProfileDoesNotResolveUnapprovedChildNames() {
        pending.setRequestedRole(RoleName.PARENT); pending.setChildRegistrationNumbers(java.util.List.of("001"));
        when(users.findByApprovedFalseOrderByCreatedAtAsc()).thenReturn(java.util.List.of(pending));
        when(childAdmission.review(2L, java.util.List.of("001"))).thenReturn(java.util.List.of("001 — Sali Diallo"));
        assertEquals(java.util.List.of(), service.findById(7L).getChildReview());
        verifyNoInteractions(childAdmission);
        assertEquals(java.util.List.of("001 — Sali Diallo"), service.findPendingApprovals(10L, true).getFirst().getChildReview());
    }

    @Test
    void parentApprovalCannotResolveClaimsInAnotherSchool() {
        School another = School.builder().id(3L).owner(school.getOwner()).status(SchoolStatus.ACTIVE).build();
        when(schools.findById(3L)).thenReturn(Optional.of(another));
        assertThrows(IllegalArgumentException.class, () -> service.approvePendingUser(7L, 3L, RoleName.PARENT, 10L, false));
        verify(childAdmission, never()).attachApproved(any(), any(), any());
        assertFalse(pending.getApproved());
    }

    @Test
    void failedResendReturnsItsDeliveryResultInsteadOfRollingBackIt() {
        pending.setEmailVerified(false);
        when(mail.sendInvitation(pending)).thenReturn(false);
        assertFalse(service.resendEmailVerification(7L));
        when(mail.sendInvitation(pending)).thenReturn(true);
        assertTrue(service.resendEmailVerification(7L));
    }

    @ParameterizedTest @EnumSource(value = RoleName.class, names = {"STUDENT", "TEACHER"})
    void publicRegistrationRequiresIdentifierWithoutResolvingPrivateNames(RoleName role) {
        var request = registration(role); request.setSchoolIdentifier(" ");
        assertThrows(IllegalArgumentException.class, () -> service.receiveRegistration(request));
        verify(users, never()).save(any());
        request.setSchoolIdentifier(" 001 "); service.receiveRegistration(request);
        verify(users).save(argThat(u -> "001".equals(u.getSchoolIdentifier())));
        verifyNoInteractions(identityAdmission);
    }

    @Test void unresolvedIdentifierPreventsApprovalAndSchoolMembership() {
        pending.setRequestedRole(RoleName.STUDENT); pending.setSchoolIdentifier("UNKNOWN");
        doThrow(new IllegalArgumentException("Matricule introuvable")).when(identityAdmission).attachApproved(pending, school, RoleName.STUDENT, "UNKNOWN");
        assertThrows(IllegalArgumentException.class, () -> service.approvePendingUser(7L, 2L, RoleName.STUDENT, 10L, false));
        verify(memberships, never()).save(any()); assertFalse(pending.getApproved());
    }

}
