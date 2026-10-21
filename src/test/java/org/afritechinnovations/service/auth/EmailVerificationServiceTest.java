package org.afritechinnovations.service.auth;

import org.afritechinnovations.model.common.EmailVerificationToken;
import org.afritechinnovations.model.common.RoleName;
import org.afritechinnovations.model.common.User;
import org.afritechinnovations.repository.common.EmailVerificationTokenRepository;
import org.afritechinnovations.repository.common.UserRepository;
import org.afritechinnovations.service.common.EmailService;
import org.afritechinnovations.service.common.ParentAutoAccessService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class EmailVerificationServiceTest {
    @Mock UserRepository users;
    @Mock EmailVerificationTokenRepository tokens;
    @Mock PasswordEncoder encoder;
    @Mock EmailService email;
    @Mock ParentAutoAccessService autoAccess;
    @Mock org.afritechinnovations.security.AuthAttemptLimiter mailAttempts;
    @Mock org.afritechinnovations.repository.common.PasswordResetTokenRepository passwordResetTokens;
    @InjectMocks EmailVerificationService service;

    private User parent;
    private EmailVerificationToken stored;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(service, "frontendBaseUrl", "http://app/");
        when(mailAttempts.allow(anyString(), org.mockito.ArgumentMatchers.anyInt())).thenReturn(true);
        parent = User.builder().id(7L).firstName("Awa").email("awa@ecole.bf").passwordHash("random")
                .passwordSet(false).active(true).approved(true).emailVerified(false).build();
        when(encoder.encode(anyString())).thenAnswer(i -> "hash:" + i.getArgument(0));
        when(tokens.save(any())).thenAnswer(i -> {
            stored = i.getArgument(0);
            return stored;
        });
        when(tokens.findByTokenHash(anyString())).thenAnswer(i ->
                stored != null && stored.getTokenHash().equals(i.getArgument(0)) ? Optional.of(stored) : Optional.empty());
        when(tokens.findByTokenHashForUpdate(anyString())).thenAnswer(i ->
                stored != null && stored.getTokenHash().equals(i.getArgument(0)) ? Optional.of(stored) : Optional.empty());
        doAnswer(i -> { stored = null; return null; }).when(tokens).delete(any());
    }

    @Test
    void correctingAnUnactivatedEmailRevokesOldLinksAndSendsANewInvitation() {
        when(users.findByIdForUpdate(7L)).thenReturn(Optional.of(parent));
        when(tokens.findByUserId(7L)).thenAnswer(call -> Optional.ofNullable(stored));
        doAnswer(call -> { stored = null; return null; }).when(tokens).deleteByUserId(7L);
        assertTrue(service.sendInvitation(parent));
        String oldToken = sentToken("activate");
        clearInvocations(email);
        assertTrue(service.correctUnactivatedEmail(7L, " CORRECT@example.test "));
        assertEquals("correct@example.test", parent.getEmail());
        assertFalse(parent.getPasswordSet()); assertFalse(parent.getEmailVerified());
        assertEquals(1, parent.getSessionVersion());
        verify(email).sendText(eq("correct@example.test"), anyString(), anyString());
        verify(passwordResetTokens).deleteByUserId(7L);
        assertEquals("SENT", stored.getDeliveryStatus());
        assertThrows(IllegalArgumentException.class, () -> service.confirm(oldToken, "password123"));
    }

    @Test
    void correctionRefusesTakenEmailsAndAccountsAlreadyTakenInHand() {
        when(users.findByIdForUpdate(7L)).thenReturn(Optional.of(parent));
        when(users.findByEmailIgnoreCase("taken@example.test")).thenReturn(Optional.of(User.builder().id(8L).build()));
        assertThrows(IllegalArgumentException.class, () -> service.correctUnactivatedEmail(7L, "taken@example.test"));
        parent.setPasswordSet(true);
        assertThrows(IllegalArgumentException.class, () -> service.correctUnactivatedEmail(7L, "new@example.test"));
        parent.setPasswordSet(false); parent.setEmailVerified(true);
        assertThrows(IllegalArgumentException.class, () -> service.correctUnactivatedEmail(7L, "new@example.test"));
        assertEquals("awa@ecole.bf", parent.getEmail());
        verifyNoInteractions(email); verify(tokens, never()).deleteByUserId(any());
    }

    @Test
    void correctionRevokesOldLinksEvenWhenNewInvitationIsRateLimited() {
        when(users.findByIdForUpdate(7L)).thenReturn(Optional.of(parent));
        when(mailAttempts.allow(anyString(), org.mockito.ArgumentMatchers.anyInt())).thenReturn(false);
        assertFalse(service.correctUnactivatedEmail(7L, "new@example.test"));
        assertEquals("new@example.test", parent.getEmail());
        verify(tokens).deleteByUserId(7L); verify(passwordResetTokens).deleteByUserId(7L);
        verifyNoInteractions(email);
    }

    @Test
    void correctionKeepsNewAddressAndReportsFailedSmtpDelivery() {
        when(users.findByIdForUpdate(7L)).thenReturn(Optional.of(parent));
        doThrow(new org.springframework.mail.MailSendException("offline")).when(email).sendText(anyString(), anyString(), anyString());
        assertFalse(service.correctUnactivatedEmail(7L, "new@example.test"));
        assertEquals("new@example.test", parent.getEmail()); assertEquals("FAILED", stored.getDeliveryStatus());
        verify(passwordResetTokens).deleteByUserId(7L);
    }

    @Test
    void personalEmailChangesOnlyAfterConfirmationAndFollowsAllSchools() {
        parent.setPasswordSet(true); parent.setEmailVerified(true);
        when(users.findByIdForUpdate(7L)).thenReturn(Optional.of(parent));
        when(encoder.matches("current", "random")).thenReturn(true);
        var schoolA = org.afritechinnovations.model.people.Student.builder().user(parent).build();
        var schoolB = org.afritechinnovations.model.people.Student.builder().user(parent).build();
        assertTrue(service.requestEmailChange(7L, " NEW@example.test ", "current"));
        assertEquals("awa@ecole.bf", parent.getEmail());
        assertEquals(0, parent.getSessionVersion());
        var body = ArgumentCaptor.forClass(String.class);
        verify(email).sendText(eq("new@example.test"), anyString(), body.capture());
        var matcher = Pattern.compile("http://app/login\\?verify=([\\w-]+)").matcher(body.getValue());
        assertTrue(matcher.find());
        service.confirm(matcher.group(1), null);
        assertEquals("new@example.test", schoolA.getEmail());
        assertEquals("new@example.test", schoolB.getEmail());
        assertTrue(parent.getEmailVerified()); assertEquals(1, parent.getSessionVersion());
        verify(passwordResetTokens).deleteByUserId(7L);
        assertNull(stored);
    }

    @Test
    void emailChangeRequiresPasswordAndRefusesExistingAccount() {
        parent.setPasswordSet(true);
        when(users.findByIdForUpdate(7L)).thenReturn(Optional.of(parent));
        assertThrows(IllegalArgumentException.class, () -> service.requestEmailChange(7L, "other@example.test", "bad"));
        when(encoder.matches("current", "random")).thenReturn(true);
        when(users.findByEmailIgnoreCase("other@example.test")).thenReturn(Optional.of(User.builder().id(8L).build()));
        assertThrows(IllegalArgumentException.class, () -> service.requestEmailChange(7L, "other@example.test", "current"));
        assertEquals("awa@ecole.bf", parent.getEmail());
        verifyNoInteractions(email);
    }

    @Test
    void failedEmailChangeDeliveryKeepsCurrentAddressAndSessions() {
        parent.setPasswordSet(true);
        when(users.findByIdForUpdate(7L)).thenReturn(Optional.of(parent));
        when(encoder.matches("current", "random")).thenReturn(true);
        doThrow(new org.springframework.mail.MailSendException("offline")).when(email).sendText(anyString(), anyString(), anyString());
        assertFalse(service.requestEmailChange(7L, "new@example.test", "current"));
        assertEquals("awa@ecole.bf", parent.getEmail()); assertEquals(0, parent.getSessionVersion());
    }

    @Test
    void emailChangeLinkExpiresAndCannotTakeAnAddressClaimedMeanwhile() {
        parent.setPasswordSet(true);
        when(users.findByIdForUpdate(7L)).thenReturn(Optional.of(parent));
        when(encoder.matches("current", "random")).thenReturn(true);
        assertTrue(service.requestEmailChange(7L, "new@example.test", "current"));
        var body = ArgumentCaptor.forClass(String.class);
        verify(email).sendText(eq("new@example.test"), anyString(), body.capture());
        var matcher = Pattern.compile("http://app/login\\?verify=([\\w-]+)").matcher(body.getValue());
        assertTrue(matcher.find());
        when(users.findByEmailIgnoreCase("new@example.test")).thenReturn(Optional.of(User.builder().id(8L).build()));
        assertThrows(IllegalArgumentException.class, () -> service.confirm(matcher.group(1), null));
        assertEquals("awa@ecole.bf", parent.getEmail());
        stored.setExpiresAt(LocalDateTime.now().minusMinutes(1));
        assertThrows(IllegalArgumentException.class, () -> service.confirm(matcher.group(1), null));
    }

    @Test
    void passwordChangeInvalidatesAnOutstandingEmailChange() {
        parent.setPasswordSet(true);
        when(users.findByIdForUpdate(7L)).thenReturn(Optional.of(parent));
        when(encoder.matches("current", "random")).thenReturn(true);
        assertTrue(service.requestEmailChange(7L, "new@example.test", "current"));
        var body = ArgumentCaptor.forClass(String.class);
        verify(email).sendText(eq("new@example.test"), anyString(), body.capture());
        var matcher = Pattern.compile("http://app/login\\?verify=([\\w-]+)").matcher(body.getValue());
        assertTrue(matcher.find());
        parent.revokeSessions();
        assertThrows(IllegalArgumentException.class, () -> service.confirm(matcher.group(1), null));
        assertEquals("awa@ecole.bf", parent.getEmail());
    }

    private String sentToken(String param) {
        ArgumentCaptor<String> body = ArgumentCaptor.forClass(String.class);
        verify(email).sendText(eq("awa@ecole.bf"), anyString(), body.capture());
        Matcher m = Pattern.compile("http://app/login\\?" + param + "=([\\w-]+)").matcher(body.getValue());
        assertTrue(m.find(), body.getValue());
        return m.group(1);
    }

    @Test
    void existingVerifiedAccountGetsOneNotificationWithoutActivationOrPasswordChange() {
        parent.setPasswordSet(true);
        parent.setEmailVerified(true);
        var school = org.afritechinnovations.model.common.School.builder().id(1L).name("École ABC").build();
        assertTrue(service.sendAccessAssigned(parent, school, "Enseignant"));
        verify(email, times(1)).sendText(eq(parent.getEmail()), anyString(), org.mockito.ArgumentMatchers.contains("Changer d’espace"));
        verifyNoInteractions(tokens, users, encoder, autoAccess);
        assertEquals("random", parent.getPasswordHash());
    }

    @Test
    void newAccountGetsOneActivationEmailForDirectAssignment() {
        var school = org.afritechinnovations.model.common.School.builder().id(1L).name("École ABC").build();
        assertTrue(service.sendAccessAssigned(parent, school, "Parent"));
        sentToken("activate");
        verify(email, times(1)).sendText(eq(parent.getEmail()), anyString(), anyString());
        assertFalse(parent.getEmailVerified());
        assertTrue(parent.getApproved());
    }

    @Test
    void assignmentEmailFailureDoesNotUndoTheAssignedAccess() {
        parent.setPasswordSet(true); parent.setEmailVerified(true);
        doThrow(new org.springframework.mail.MailSendException("unavailable")).when(email).sendText(anyString(), anyString(), anyString());
        assertFalse(service.sendAccessAssigned(parent, org.afritechinnovations.model.common.School.builder().name("École").build(), "Parent"));
        assertTrue(parent.getApproved());
        verifyNoInteractions(users, tokens);
    }

    @Test
    void activationLinkLeavesTheAccountUntouchedUntilItIsOpened() {
        service.sendActivation(parent, 2L, RoleName.PARENT);

        assertEquals("random", parent.getPasswordHash());
        assertFalse(parent.getEmailVerified());
        verify(users, never()).save(any());
        verifyNoInteractions(autoAccess);
        String token = sentToken("activate");
        assertNotEquals(token, stored.getTokenHash(), "only the hash is stored");

        assertThrows(IllegalArgumentException.class, () -> service.confirm(token, "court"));
        service.confirm(token, "MonMotDePasse");

        assertEquals("hash:MonMotDePasse", parent.getPasswordHash());
        assertTrue(parent.getPasswordSet());
        assertTrue(parent.getEmailVerified());
        verify(tokens).delete(any(EmailVerificationToken.class));
        verify(autoAccess).grantFromChildren(7L, true);
        verify(autoAccess).requestSchool(7L, 2L, RoleName.PARENT);
        assertEquals(1, parent.getSessionVersion());
        assertThrows(IllegalArgumentException.class, () -> service.confirm(token, "AutreMotDePasse"));
    }

    @Test
    void verificationLinkConfirmsTheAddressWithoutChangingThePassword() {
        parent.setPasswordSet(true);
        service.sendVerification(parent);

        service.confirm(sentToken("verify"), null);

        assertTrue(parent.getEmailVerified());
        assertEquals("random", parent.getPasswordHash());
        verify(autoAccess).grantFromChildren(7L, true);
        verify(autoAccess, never()).requestSchool(any(), any(), any());
    }

    @Test
    void rejectsUnknownOrExpiredLinks() {
        assertThrows(IllegalArgumentException.class, () -> service.confirm("inconnu", "MonMotDePasse"));

        service.sendVerification(parent);
        String token = sentToken("verify");
        stored.setExpiresAt(LocalDateTime.now().minusMinutes(1));
        assertThrows(IllegalArgumentException.class, () -> service.confirm(token, null));
        assertFalse(parent.getEmailVerified());
        verifyNoInteractions(autoAccess);
    }

    @Test
    void doesNotSendVerificationToAnAlreadyVerifiedAddress() {
        parent.setEmailVerified(true);
        service.sendVerification(parent);
        verifyNoInteractions(email, tokens);
    }

    @Test
    void activationClaimsAreTransferredOnlyAfterEmailProofWithoutCreatingChildLinks() {
        service.sendActivation(parent, 2L, RoleName.PARENT, java.util.List.of(" 001 ", "002", "001"));
        assertEquals(java.util.List.of(), parent.getChildRegistrationNumbers());
        assertEquals(java.util.List.of("001", "002"), stored.getChildRegistrationNumbers());
        verifyNoInteractions(autoAccess);
        service.confirm(sentToken("activate"), "MonMotDePasse");
        assertEquals(java.util.List.of("001", "002"), parent.getChildRegistrationNumbers());
        verify(autoAccess).requestSchool(7L, 2L, RoleName.PARENT);
    }

    @Test
    void resendingActivationPreservesItsRequestedSchoolAndChildMatricules() {
        service.sendActivation(parent, 2L, RoleName.PARENT, java.util.List.of("001"));
        String oldHash = stored.getTokenHash();
        when(tokens.findByUserId(7L)).thenReturn(Optional.of(stored));
        service.sendInvitation(parent);
        assertNotEquals(oldHash, stored.getTokenHash());
        assertEquals(2L, stored.getRequestedSchoolId());
        assertEquals(RoleName.PARENT, stored.getRequestedRole());
        assertEquals(java.util.List.of("001"), stored.getChildRegistrationNumbers());
        assertEquals(java.util.List.of(), parent.getChildRegistrationNumbers());
    }

    @Test
    void failedSmtpDeliveryIsPersistedAndCanBeRetried() {
        doThrow(new org.springframework.mail.MailSendException("offline")).when(email).sendText(anyString(), anyString(), anyString());
        assertFalse(service.sendInvitation(parent));
        assertEquals("FAILED", stored.getDeliveryStatus());
        assertEquals(parent.getEmail(), stored.getRecipientEmail());
        String failedHash = stored.getTokenHash();
        doNothing().when(email).sendText(anyString(), anyString(), anyString());
        assertTrue(service.sendInvitation(parent));
        assertEquals("SENT", stored.getDeliveryStatus());
        assertNotEquals(failedHash, stored.getTokenHash());
        verify(tokens, times(2)).deleteByUserId(7L);
        assertFalse(parent.getEmailVerified());
    }

    @Test
    void activationRejectsPrivilegedRolesBeforeSendingAnything() {
        for (RoleName role : new RoleName[]{RoleName.SUPER_ADMIN, RoleName.SCHOOL_ADMIN, RoleName.STAFF}) {
            assertThrows(IllegalArgumentException.class, () -> service.sendActivation(parent, 2L, role));
        }
        verifyNoInteractions(email, tokens, users);
    }

    @Test
    void oldTokenCannotVerifyAnAddressChangedByAnAdministrator() {
        service.sendVerification(parent);
        String token = sentToken("verify");
        parent.setEmail("other@ecole.bf");
        assertThrows(IllegalArgumentException.class, () -> service.confirm(token, null));
        assertFalse(parent.getEmailVerified());
        verifyNoInteractions(users, autoAccess);
    }

    @Test
    void legacyActivationWithPrivilegedRoleCannotGrantAccess() {
        service.sendActivation(parent, 2L, RoleName.PARENT);
        String token = sentToken("activate");
        stored.setRequestedRole(RoleName.SUPER_ADMIN);
        assertThrows(IllegalArgumentException.class, () -> service.confirm(token, "MonMotDePasse"));
        assertFalse(parent.getPasswordSet());
        verifyNoInteractions(users, autoAccess);
    }

    @Test
    void activationCannotOverwriteAPasswordAlreadyChosenOrReset() {
        service.sendInvitation(parent);
        String token = sentToken("activate");
        parent.setPasswordSet(true);
        assertThrows(IllegalArgumentException.class, () -> service.confirm(token, "MonMotDePasse"));
        assertEquals("random", parent.getPasswordHash());
    }

    @Test
    void inactiveAccountsCannotBeInvitedOrActivated() {
        service.sendInvitation(parent);
        String token = sentToken("activate");
        parent.setActive(false);
        assertFalse(service.sendInvitation(parent));
        assertThrows(IllegalArgumentException.class, () -> service.confirm(token, "MonMotDePasse"));
        verify(users, never()).save(any());
        verifyNoInteractions(autoAccess);
    }

    @Test
    void invitationRateLimitDoesNotInvalidateThePreviousLink() {
        ReflectionTestUtils.setField(service, "mailAttempts", new org.afritechinnovations.security.AuthAttemptLimiter());
        assertTrue(service.sendInvitation(parent)); assertTrue(service.sendInvitation(parent)); assertTrue(service.sendInvitation(parent));
        EmailVerificationToken previous = stored;
        assertFalse(service.sendInvitation(parent)); assertSame(previous, stored);
        verify(email, times(3)).sendText(anyString(), anyString(), anyString());
    }

    @Test void activationIdentifierIsKeptOnRetryAndTransferredOnlyAfterEmailProof() {
        service.sendActivation(parent, 2L, RoleName.TEACHER, java.util.List.of(), "EMP-001");
        assertNull(parent.getSchoolIdentifier()); assertEquals("EMP-001", stored.getSchoolIdentifier());
        when(tokens.findByUserId(7L)).thenReturn(Optional.of(stored));
        service.sendInvitation(parent);
        assertEquals("EMP-001", stored.getSchoolIdentifier());
        org.mockito.ArgumentCaptor<String> bodies = org.mockito.ArgumentCaptor.forClass(String.class);
        verify(email, times(2)).sendText(anyString(), anyString(), bodies.capture());
        String latest = bodies.getAllValues().getLast().split("activate=")[1].split("\\s")[0];
        service.confirm(latest, "MonMotDePasse");
        assertEquals("EMP-001", parent.getSchoolIdentifier());
    }

}
