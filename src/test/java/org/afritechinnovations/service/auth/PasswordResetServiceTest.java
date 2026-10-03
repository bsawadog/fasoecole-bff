package org.afritechinnovations.service.auth;

import org.afritechinnovations.model.common.PasswordResetToken;
import org.afritechinnovations.model.common.User;
import org.afritechinnovations.repository.common.PasswordResetTokenRepository;
import org.afritechinnovations.repository.common.UserRepository;
import org.afritechinnovations.service.common.EmailService;
import org.afritechinnovations.service.common.ParentAutoAccessService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;
import java.time.LocalDateTime;
import java.util.Optional;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class PasswordResetServiceTest {
    @Mock UserRepository users;
    @Mock PasswordResetTokenRepository tokens;
    @Mock PasswordEncoder encoder;
    @Mock EmailService email;
    @Mock ParentAutoAccessService parents;
    @Mock org.afritechinnovations.security.AuthAttemptLimiter mailAttempts;
    @InjectMocks PasswordResetService service;
    User user;
    PasswordResetToken stored;
    String raw;

    @BeforeEach
    void setup() {
        ReflectionTestUtils.setField(service, "frontendBaseUrl", "http://app/");
        ReflectionTestUtils.setField(service, "tokenExpirationMinutes", 30L);
        when(mailAttempts.allow(anyString(), anyInt())).thenReturn(true);
        user = User.builder().id(7L).email("awa@test.bf").passwordHash("old").emailVerified(true).build();
        when(users.findByEmailIgnoreCase("awa@test.bf")).thenAnswer(i -> Optional.of(user));
        when(tokens.save(any())).thenAnswer(i -> stored = i.getArgument(0));
        when(tokens.findByTokenHashForUpdate(anyString())).thenAnswer(i ->
                stored != null && stored.getTokenHash().equals(i.getArgument(0)) ? Optional.of(stored) : Optional.empty());
        doAnswer(i -> { stored = null; return null; }).when(tokens).delete(any());
        doAnswer(i -> { stored = null; return null; }).when(tokens).deleteByUserId(anyLong());
        when(encoder.encode(anyString())).thenAnswer(i -> "hash:" + i.getArgument(0));
        doAnswer(i -> { String url = i.getArgument(1); raw = url.substring(url.indexOf("token=") + 6); return null; })
                .when(email).sendPasswordReset(anyString(), anyString(), anyLong());
    }

    @Test
    void resetLinkChangesPasswordRevokesSessionsAndCannotBeReplayed() {
        service.requestReset(" awa@test.bf ");
        assertNotEquals(raw, stored.getTokenHash());
        assertEquals(user.getEmail(), stored.getRecipientEmail());
        service.resetPassword(raw, "NouveauMotDePasse");
        assertEquals("hash:NouveauMotDePasse", user.getPasswordHash());
        assertEquals(1, user.getSessionVersion());
        assertNull(stored);
        assertThrows(IllegalArgumentException.class, () -> service.resetPassword(raw, "AutreMotDePasse"));
        verify(users, times(1)).save(user);
    }

    @Test
    void missingAndInactiveAccountsDoNotSendMail() {
        service.requestReset("absent@test.bf");
        user.setActive(false);
        service.requestReset(user.getEmail());
        verifyNoInteractions(email, tokens);
    }

    @Test
    void pendingAccountCanRecoverPasswordWithoutBypassingApproval() {
        user.setApproved(false); user.setEmailVerified(false);
        service.requestReset(user.getEmail());
        service.resetPassword(raw, "NouveauMotDePasse");
        assertTrue(user.getEmailVerified()); assertFalse(user.getApproved());
        assertEquals("hash:NouveauMotDePasse", user.getPasswordHash());
    }

    @Test
    void accountMailLimitPreservesExistingLinkAndDoesNotSendAnotherMail() {
        service.requestReset(user.getEmail()); String firstHash = stored.getTokenHash();
        when(mailAttempts.allow(anyString(), anyInt())).thenReturn(false);
        assertFalse(service.sendManagedReset(user));
        assertEquals(firstHash, stored.getTokenHash());
        verify(email, times(1)).sendPasswordReset(anyString(), anyString(), anyLong());
    }

    @Test
    void expiredTokenCannotChangePassword() {
        service.requestReset(user.getEmail());
        stored.setExpiresAt(LocalDateTime.now().minusSeconds(1));
        assertThrows(IllegalArgumentException.class, () -> service.resetPassword(raw, "NouveauMotDePasse"));
        assertEquals("old", user.getPasswordHash());
        assertEquals(0, user.getSessionVersion());
        verify(users, never()).save(any());
    }

    @Test
    void accountDeactivatedAfterMailCannotUseResetLink() {
        service.requestReset(user.getEmail()); user.setActive(false);
        assertThrows(IllegalArgumentException.class, () -> service.resetPassword(raw, "NouveauMotDePasse"));
        assertEquals("old", user.getPasswordHash());
    }

    @Test
    void linkSentToFormerAddressCannotVerifyNewEmail() {
        service.requestReset(user.getEmail()); user.setEmail("other@test.bf");
        assertThrows(IllegalArgumentException.class, () -> service.resetPassword(raw, "NouveauMotDePasse"));
        assertEquals("old", user.getPasswordHash());
    }

    @Test
    void firstPasswordThroughResetProvesEmailAndGrantsParentAccess() {
        user.setEmailVerified(false); user.setPasswordSet(false);
        service.requestReset(user.getEmail());
        service.resetPassword(raw, "NouveauMotDePasse");
        assertTrue(user.getEmailVerified()); assertTrue(user.getPasswordSet());
        verify(parents).grantFromChildren(7L, true);
    }

    @Test
    void failedDeliveryReturnsFalseAndDoesNotChangeExistingPassword() {
        doThrow(new org.springframework.mail.MailSendException("offline")).when(email).sendPasswordReset(anyString(), anyString(), anyLong());
        assertFalse(service.sendManagedReset(user));
        assertNull(stored);
        assertEquals("old", user.getPasswordHash());
        assertEquals(0, user.getSessionVersion());
        verify(users, never()).save(any());
    }

    @Test
    void newResetLinkReplacesThePreviousLink() {
        service.requestReset(user.getEmail()); String first = raw;
        service.requestReset(user.getEmail());
        assertNotEquals(first, raw);
        assertThrows(IllegalArgumentException.class, () -> service.resetPassword(first, "NouveauMotDePasse"));
        assertDoesNotThrow(() -> service.resetPassword(raw, "NouveauMotDePasse"));
    }
}
