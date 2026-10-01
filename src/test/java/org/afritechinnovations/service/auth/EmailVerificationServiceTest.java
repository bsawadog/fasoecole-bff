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
    @InjectMocks EmailVerificationService service;

    private User parent;
    private EmailVerificationToken stored;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(service, "frontendBaseUrl", "http://app/");
        parent = User.builder().id(7L).firstName("Awa").email("awa@ecole.bf").passwordHash("random")
                .passwordSet(false).active(true).approved(true).emailVerified(false).build();
        when(encoder.encode(anyString())).thenAnswer(i -> "hash:" + i.getArgument(0));
        when(tokens.save(any())).thenAnswer(i -> {
            stored = i.getArgument(0);
            return stored;
        });
        when(tokens.findByTokenHash(anyString())).thenAnswer(i ->
                stored != null && stored.getTokenHash().equals(i.getArgument(0)) ? Optional.of(stored) : Optional.empty());
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
        verify(tokens).delete(stored);
        verify(autoAccess).grantFromChildren(7L, true);
        verify(autoAccess).requestSchool(7L, 2L, RoleName.PARENT);
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
}
