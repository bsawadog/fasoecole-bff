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

    private String sentToken(String param) {
        ArgumentCaptor<String> body = ArgumentCaptor.forClass(String.class);
        verify(email).sendText(eq("awa@ecole.bf"), anyString(), body.capture());
        Matcher m = Pattern.compile("http://app/login\\?" + param + "=([\\w-]+)").matcher(body.getValue());
        assertTrue(m.find(), body.getValue());
        return m.group(1);
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
