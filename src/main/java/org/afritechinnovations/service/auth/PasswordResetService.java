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

    @Value("${app.frontend.base-url}")
    private String frontendBaseUrl;

    @Value("${app.password-reset.token-expiration-minutes}")
    private long tokenExpirationMinutes;

    @Transactional
    public void requestReset(String email) {
        userRepository.findByEmailIgnoreCase(email.trim())
                .filter(user -> Boolean.TRUE.equals(user.getActive()) && Boolean.TRUE.equals(user.getApproved()))
                .ifPresent(this::createAndSendResetLink);
    }

    @Transactional
    public void resetPassword(String rawToken, String newPassword) {
        String tokenHash = hash(rawToken);
        PasswordResetToken resetToken = tokenRepository.findByTokenHash(tokenHash)
                .orElseThrow(() -> new IllegalArgumentException("Lien de réinitialisation invalide ou expiré"));

        if (!resetToken.getExpiresAt().isAfter(LocalDateTime.now())) {
            tokenRepository.delete(resetToken);
            throw new IllegalArgumentException("Lien de réinitialisation invalide ou expiré");
        }

        User user = resetToken.getUser();
        if (!Boolean.TRUE.equals(user.getActive()) || !Boolean.TRUE.equals(user.getApproved())) {
            tokenRepository.delete(resetToken);
            throw new IllegalArgumentException("Lien de réinitialisation invalide ou expiré");
        }

        user.setPasswordHash(passwordEncoder.encode(newPassword));
        userRepository.save(user);
        tokenRepository.delete(resetToken);
    }

    private void createAndSendResetLink(User user) {
        byte[] randomBytes = new byte[32];
        SECURE_RANDOM.nextBytes(randomBytes);
        String rawToken = Base64.getUrlEncoder().withoutPadding().encodeToString(randomBytes);
        String tokenHash = hash(rawToken);

        tokenRepository.deleteByUserId(user.getId());
        tokenRepository.save(PasswordResetToken.builder()
                .user(user)
                .tokenHash(tokenHash)
                .expiresAt(LocalDateTime.now().plusMinutes(tokenExpirationMinutes))
                .build());

        String resetUrl = frontendBaseUrl.replaceAll("/+$", "") + "/login?token=" + rawToken;
        try {
            emailService.sendPasswordReset(user.getEmail(), resetUrl, tokenExpirationMinutes);
        } catch (MailException ex) {
            tokenRepository.deleteByUserId(user.getId());
            log.error("Password reset email delivery failed; SMTP configuration should be checked");
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
