package org.afritechinnovations.service.auth;

import lombok.RequiredArgsConstructor;
import org.afritechinnovations.model.common.PasswordResetToken;
import org.afritechinnovations.model.common.User;
import org.afritechinnovations.repository.common.PasswordResetTokenRepository;
import org.afritechinnovations.repository.common.UserRepository;
import org.afritechinnovations.service.common.EmailService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.MailException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.HexFormat;

@Service
@RequiredArgsConstructor
public class PasswordResetService {

    private static final Logger log = LoggerFactory.getLogger(PasswordResetService.class);
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private final UserRepository userRepository;
    private final PasswordResetTokenRepository tokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final EmailService emailService;
    private final org.afritechinnovations.service.common.ParentAutoAccessService parentAutoAccessService;
    private final org.afritechinnovations.security.AuthAttemptLimiter mailAttempts;

    @Value("${app.frontend.base-url}")
    private String frontendBaseUrl;

    @Value("${app.password-reset.token-expiration-minutes}")
    private long tokenExpirationMinutes;

    @Transactional
    public void requestReset(String email) {
        userRepository.findByEmailIgnoreCase(email.trim())
                .filter(user -> Boolean.TRUE.equals(user.getActive()))
                .ifPresent(this::createAndSendResetLink);
    }

    @Transactional
    public void resetPassword(String rawToken, String newPassword) {
        if (rawToken == null || rawToken.isBlank()) throw new IllegalArgumentException("Lien de réinitialisation invalide ou expiré");
        if (newPassword == null || newPassword.length() < 8 || newPassword.length() > 100) throw new IllegalArgumentException("Le mot de passe doit contenir de 8 à 100 caractères");
        String tokenHash = hash(rawToken);
        PasswordResetToken resetToken = tokenRepository.findByTokenHashForUpdate(tokenHash)
                .orElseThrow(() -> new IllegalArgumentException("Lien de réinitialisation invalide ou expiré"));

        if (!resetToken.getExpiresAt().isAfter(LocalDateTime.now())) {
            tokenRepository.delete(resetToken);
            throw new IllegalArgumentException("Lien de réinitialisation invalide ou expiré");
        }

        User user = resetToken.getUser();
        if (resetToken.getRecipientEmail() == null || !resetToken.getRecipientEmail().equalsIgnoreCase(user.getEmail())) {
            throw new IllegalArgumentException("Lien de réinitialisation invalide ou expiré");
        }
        if (!Boolean.TRUE.equals(user.getActive())) {
            tokenRepository.delete(resetToken);
            throw new IllegalArgumentException("Lien de réinitialisation invalide ou expiré");
        }

        user.setPasswordHash(passwordEncoder.encode(newPassword));
        user.revokeSessions();
        // Le lien a été reçu dans la boîte : l'adresse est prouvée.
        boolean newlyVerified = !Boolean.TRUE.equals(user.getEmailVerified()) || !Boolean.TRUE.equals(user.getPasswordSet());
        user.setPasswordSet(true);
        user.setEmailVerified(true);
        userRepository.save(user);
        tokenRepository.delete(resetToken);
        if (newlyVerified) {
            parentAutoAccessService.grantFromChildren(user.getId(), true);
        }
    }

    @Transactional
    public boolean sendManagedReset(User user) {
        if (!Boolean.TRUE.equals(user.getActive())) throw new IllegalArgumentException("Compte inactif");
        return createAndSendResetLink(user);
    }

    private boolean createAndSendResetLink(User user) {
        if (!mailAttempts.allow("account-mail:" + user.getId(), 3)) return false;
        byte[] randomBytes = new byte[32];
        SECURE_RANDOM.nextBytes(randomBytes);
        String rawToken = Base64.getUrlEncoder().withoutPadding().encodeToString(randomBytes);
        String tokenHash = hash(rawToken);

        tokenRepository.deleteByUserId(user.getId());
        tokenRepository.flush();
        tokenRepository.save(PasswordResetToken.builder()
                .user(user)
                .tokenHash(tokenHash)
                .recipientEmail(user.getEmail())
                .expiresAt(LocalDateTime.now().plusMinutes(tokenExpirationMinutes))
                .build());

        String resetUrl = frontendBaseUrl.replaceAll("/+$", "") + "/login?token=" + rawToken;
        try {
            emailService.sendPasswordReset(user.getEmail(), resetUrl, tokenExpirationMinutes);
            return true;
        } catch (MailException ex) {
            tokenRepository.deleteByUserId(user.getId());
            log.error("Password reset email delivery failed: {}: {}", ex.getClass().getSimpleName(), ex.getMessage());
            return false;
        }
    }

    private String hash(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 is unavailable", ex);
        }
    }
}
