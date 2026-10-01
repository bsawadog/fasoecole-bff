package org.afritechinnovations.service.auth;

import lombok.RequiredArgsConstructor;
import org.afritechinnovations.model.common.EmailVerificationToken;
import org.afritechinnovations.model.common.RoleName;
import org.afritechinnovations.model.common.User;
import org.afritechinnovations.repository.common.EmailVerificationTokenRepository;
import org.afritechinnovations.repository.common.UserRepository;
import org.afritechinnovations.service.common.EmailService;
import org.afritechinnovations.service.common.ParentAutoAccessService;
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

/**
 * Preuve de possession du courriel avant tout accès automatique :
 * <ul>
 *   <li>VERIFY : confirmer l'adresse d'un compte créé par son titulaire ;</li>
 *   <li>ACTIVATE : prendre en main un compte créé par une école (parent saisi à l'inscription d'un enfant).
 *   Le mot de passe est choisi depuis le lien : personne ne peut s'approprier le compte sans accès à la boîte.</li>
 * </ul>
 */
@Service
@RequiredArgsConstructor
public class EmailVerificationService {

    private static final Logger log = LoggerFactory.getLogger(EmailVerificationService.class);
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private final UserRepository userRepository;
    private final EmailVerificationTokenRepository tokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final EmailService emailService;
    private final ParentAutoAccessService parentAutoAccessService;

    @Value("${app.frontend.base-url}")
    private String frontendBaseUrl;

    @Value("${app.email-verification.token-expiration-hours:24}")
    private long expirationHours = 24;

    /** Envoie (ou renvoie) le lien de confirmation d'adresse. Sans effet si l'adresse est déjà vérifiée. */
    @Transactional
    public void sendVerification(User user) {
        if (user.getEmail() == null || Boolean.TRUE.equals(user.getEmailVerified())) {
            return;
        }
        String token = store(user, EmailVerificationToken.Purpose.VERIFY, null, null);
        deliver(user, "Confirmez votre adresse FasoÉcole", """
                Bonjour %s,

                Pour confirmer votre adresse courriel et sécuriser votre compte FasoÉcole, ouvrez ce lien
                (valide pendant %d heures) :

                %s

                Si vous n'êtes pas à l'origine de cette inscription, ignorez cet e-mail.
                """, link("verify", token));
    }

    /** Lien d'activation d'un compte créé par une école ; le compte n'est pas modifié avant le clic. */
    @Transactional
    public void sendActivation(User user, Long requestedSchoolId, RoleName requestedRole) {
        String token = store(user, EmailVerificationToken.Purpose.ACTIVATE, requestedSchoolId, requestedRole);
        deliver(user, "Activez votre compte FasoÉcole", """
                Bonjour %s,

                Un établissement a enregistré votre adresse comme parent ou tuteur d'un élève.
                Pour activer votre compte FasoÉcole et choisir votre mot de passe, ouvrez ce lien
                (valide pendant %d heures) :

                %s

                Si vous n'êtes pas à l'origine de cette demande, ignorez cet e-mail : votre compte reste inchangé.
                """, link("activate", token));
    }

    /**
     * Consomme le jeton : l'adresse est vérifiée, le mot de passe est défini (activation) et l'accès
     * automatique aux établissements des enfants est accordé.
     */
    @Transactional
    public void confirm(String rawToken, String newPassword) {
        EmailVerificationToken token = tokenRepository.findByTokenHash(hash(rawToken))
                .orElseThrow(() -> new IllegalArgumentException("Lien de vérification invalide ou expiré"));
        if (!token.getExpiresAt().isAfter(LocalDateTime.now())) {
            tokenRepository.delete(token);
            throw new IllegalArgumentException("Lien de vérification invalide ou expiré");
        }
        User user = token.getUser();
        if (!Boolean.TRUE.equals(user.getActive())) {
            tokenRepository.delete(token);
            throw new IllegalArgumentException("Lien de vérification invalide ou expiré");
        }
        boolean activation = token.getPurpose() == EmailVerificationToken.Purpose.ACTIVATE;
        if (activation) {
            if (newPassword == null || newPassword.length() < 8 || newPassword.length() > 100) {
                throw new IllegalArgumentException("Choisissez un mot de passe de 8 caractères minimum");
            }
            user.setPasswordHash(passwordEncoder.encode(newPassword));
            user.setPasswordSet(true);
            if (token.getRequestedSchoolId() != null && token.getRequestedRole() != null) {
                user.setRequestedSchoolId(token.getRequestedSchoolId());
                user.setRequestedRole(token.getRequestedRole());
            }
        }
        user.setEmailVerified(true);
        userRepository.save(user);
        tokenRepository.delete(token);
        parentAutoAccessService.grantFromChildren(user.getId(), true);
        if (activation) {
            parentAutoAccessService.requestSchool(user.getId(), token.getRequestedSchoolId(), token.getRequestedRole());
        }
    }

    private String store(User user, EmailVerificationToken.Purpose purpose, Long schoolId, RoleName role) {
        byte[] randomBytes = new byte[32];
        SECURE_RANDOM.nextBytes(randomBytes);
        String rawToken = Base64.getUrlEncoder().withoutPadding().encodeToString(randomBytes);
        tokenRepository.deleteByUserId(user.getId());
        tokenRepository.save(EmailVerificationToken.builder()
                .user(user)
                .tokenHash(hash(rawToken))
                .purpose(purpose)
                .requestedSchoolId(schoolId)
                .requestedRole(role)
                .expiresAt(LocalDateTime.now().plusHours(expirationHours))
                .build());
        return rawToken;
    }

    private String link(String param, String token) {
        return frontendBaseUrl.replaceAll("/+$", "") + "/login?" + param + "=" + token;
    }

    private void deliver(User user, String subject, String template, String url) {
        try {
            emailService.sendText(user.getEmail(), subject, template.formatted(user.getFirstName(), expirationHours, url));
        } catch (MailException ex) {
            log.error("Email verification delivery failed; SMTP configuration should be checked");
        }
    }

    private String hash(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 indisponible", ex);
        }
    }
}
