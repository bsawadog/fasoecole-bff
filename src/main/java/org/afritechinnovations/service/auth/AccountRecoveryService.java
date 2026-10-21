package org.afritechinnovations.service.auth;

import lombok.RequiredArgsConstructor;
import org.afritechinnovations.dto.auth.AccountRecoveryDto.*;
import org.afritechinnovations.model.common.*;
import org.afritechinnovations.repository.common.*;
import org.afritechinnovations.repository.people.*;
import org.afritechinnovations.security.AccessGuard;
import org.afritechinnovations.security.AuthAttemptLimiter;
import org.afritechinnovations.service.common.EmailService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.MailException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.text.Normalizer;
import java.time.LocalDateTime;
import java.util.*;
import static org.afritechinnovations.model.common.AccountRecoveryRequest.Status.*;

@Service @RequiredArgsConstructor @Transactional
public class AccountRecoveryService {
    private final AccountRecoveryRequestRepository requests;
    private final AccountRecoveryEventRepository events;
    private final StudentRepository students;
    private final UserRepository users;
    private final SchoolRepository schools;
    private final SchoolUserRepository memberships;
    private final ParentRepository parents;
    private final TeacherRepository teachers;
    private final EmailVerificationTokenRepository verificationTokens;
    private final PasswordResetTokenRepository resetTokens;
    private final PasswordEncoder encoder;
    private final EmailService mail;
    private final AuthAttemptLimiter limiter;
    private final AccessGuard guard;
    @Value("${app.frontend.base-url}") private String frontendBaseUrl;
    private static final SecureRandom RANDOM = new SecureRandom();

    /** Always returns the same public response; never disclose account existence or its current email. */
    public void submit(Submit input) {
        var student = students.findBySchoolIdAndRegistrationNumber(input.schoolId(), input.registrationNumber().trim()).orElse(null);
        if (student == null || student.getSchool().getStatus() != SchoolStatus.ACTIVE
                || !normalize(student.getFirstName()).equals(normalize(input.firstName()))
                || !normalize(student.getLastName()).equals(normalize(input.lastName()))
                || !Boolean.TRUE.equals(student.getUser().getActive())) return;
        User user = users.findByIdForUpdate(student.getUser().getId()).orElse(null);
        if (user == null || !Boolean.TRUE.equals(user.getActive())) return;
        if (!limiter.allow("recovery-request:" + user.getId(), 2)) return;
        var pending = requests.findByUserIdAndStatusIn(user.getId(), List.of(PENDING, SUPPORT_REQUIRED, APPROVED));
        if (pending.stream().anyMatch(prior -> prior.getStatus() != APPROVED || prior.getExpiresAt() == null
                || prior.getExpiresAt().isAfter(LocalDateTime.now()))) return;
        var request = AccountRecoveryRequest.builder().school(student.getSchool()).student(student).user(user)
                .firstName(input.firstName().trim()).lastName(input.lastName().trim())
                .registrationNumber(input.registrationNumber().trim()).newEmail(input.newEmail().trim().toLowerCase(Locale.ROOT))
                .reason(input.reason().trim()).createdAt(LocalDateTime.now()).build();
        request.setStatus(ownerCanRecover(request, student.getSchool().getOwner().getId()) ? PENDING : SUPPORT_REQUIRED);
        requests.save(request);
        audit(request, null, "SUBMITTED", "Demande publique reçue ; identité à vérifier.");
    }

    @Transactional(readOnly=true)
    public List<View> list(Long schoolId) {
        boolean support = guard.isSuperAdmin();
        if (schoolId != null) {
            guard.requireOwnedSchool(schoolId);
            return requests.findTop100BySchoolIdOrderByCreatedAtDesc(schoolId).stream().map(this::view).toList();
        }
        return (support ? requests.findTop100ByOrderByCreatedAtDesc()
                : requests.findTop100BySchoolOwnerIdOrderByCreatedAtDesc(guard.currentUserId())).stream().map(this::view).toList();
    }

    public View decide(Long id, Decision decision) {
        var request = requireRequest(id);
        authorize(request);
        if (request.getStatus() != PENDING && request.getStatus() != SUPPORT_REQUIRED
                && !(request.getStatus() == APPROVED && !decision.approve()))
            throw new IllegalArgumentException("Cette demande a déjà été traitée.");
        if (decision.verificationNote().trim().length() < 10)
            throw new IllegalArgumentException("Décrivez la vérification de l’identité ou le motif du refus.");
        if (!decision.approve()) {
            boolean cancelled = request.getStatus() == APPROVED;
            request.setStatus(REJECTED); recordDecision(request, decision.verificationNote());
            request.setTokenHash(null);
            audit(request, guard.currentUserId(), cancelled ? "CANCELLED" : "REJECTED", decision.verificationNote());
            return view(requests.save(request));
        }
        if (!decision.identityVerified()) throw new IllegalArgumentException("Confirmez avoir vérifié l’identité auprès de l’apprenant ou de son responsable légal.");
        User user = users.findByIdForUpdate(request.getUser().getId()).orElseThrow();
        requireCurrentDossier(request, user);
        if (!guard.isSuperAdmin() && !ownerCanRecover(request, guard.currentUserId())) {
            request.setStatus(SUPPORT_REQUIRED); audit(request, guard.currentUserId(), "SUPPORT_REQUIRED", "Le compte dépasse le périmètre du propriétaire.");
            return view(requests.save(request));
        }
        requireAvailableEmail(request, user);
        recordDecision(request, decision.verificationNote());
        request.setStatus(APPROVED);
        audit(request, guard.currentUserId(), "APPROVED", decision.verificationNote());
        sendLink(request, user);
        return view(requests.save(request));
    }

    public View resend(Long id) {
        var request = requireRequest(id); authorize(request);
        if (request.getStatus() != APPROVED) throw new IllegalArgumentException("Seule une demande approuvée peut recevoir un nouveau lien.");
        if (request.getDecidedAt() == null || request.getDecidedAt().isBefore(LocalDateTime.now().minusDays(1)))
            throw new IllegalArgumentException("La vérification d’identité date de plus de 24 heures. Annulez cette récupération et demandez une nouvelle vérification.");
        User user = users.findByIdForUpdate(request.getUser().getId()).orElseThrow();
        requireCurrentDossier(request, user);
        if (!Objects.equals(request.getPreviousEmail(), user.getEmail())
                || !Objects.equals(request.getRequestSessionVersion(), user.getSessionVersion()))
            throw new IllegalArgumentException("Le compte a changé depuis l’approbation. Une nouvelle demande et une nouvelle vérification sont nécessaires.");
        if (!guard.isSuperAdmin() && !ownerCanRecover(request, guard.currentUserId())) {
            request.setStatus(SUPPORT_REQUIRED); request.setTokenHash(null);
            audit(request, guard.currentUserId(), "SUPPORT_REQUIRED", "Le compte dépasse désormais le périmètre du propriétaire.");
            return view(requests.save(request));
        }
        requireAvailableEmail(request, user);
        sendLink(request, user);
        return view(requests.save(request));
    }

    public void complete(Complete input) {
        var request = requests.findByTokenHashForUpdate(hash(input.token())).orElseThrow(this::invalidLink);
        if (request.getStatus() != APPROVED || request.getExpiresAt() == null || !request.getExpiresAt().isAfter(LocalDateTime.now())) throw invalidLink();
        User user = users.findByIdForUpdate(request.getUser().getId()).orElseThrow(this::invalidLink);
        requireCurrentDossier(request, user);
        if (!Objects.equals(request.getPreviousEmail(), user.getEmail())
                || !Objects.equals(request.getRequestSessionVersion(), user.getSessionVersion())
                || (!request.isSupportDecision() && !ownerCanRecover(request, request.getDecidedBy()))) throw invalidLink();
        requireAvailableEmail(request, user);
        user.setEmail(request.getNewEmail()); user.setEmailVerified(true);
        user.setPasswordHash(encoder.encode(input.newPassword())); user.setPasswordSet(true);
        user.setMustChangePassword(false); user.revokeSessions(); users.saveAndFlush(user);
        resetTokens.deleteByUserId(user.getId());
        verificationTokens.deleteByUserId(user.getId());
        for (var other : requests.findByUserIdAndStatusIn(user.getId(), List.of(PENDING, SUPPORT_REQUIRED, APPROVED))) {
            if (!other.getId().equals(request.getId())) {
                other.setStatus(SUPERSEDED); other.setTokenHash(null); requests.save(other);
                audit(other, user.getId(), "SUPERSEDED", "Une autre récupération de ce compte a été terminée.");
            }
        }
        request.setStatus(COMPLETED); request.setTokenHash(null); request.setCompletedAt(LocalDateTime.now());
        requests.save(request);
        audit(request, user.getId(), "COMPLETED", "Nouvelle adresse confirmée, mot de passe remplacé et anciennes sessions invalidées.");
    }

    private void sendLink(AccountRecoveryRequest request, User user) {
        if (!limiter.allow("recovery-mail:" + user.getId(), 3)) throw new IllegalArgumentException("Trop d’envois. Réessayez dans une minute.");
        byte[] bytes = new byte[32]; RANDOM.nextBytes(bytes);
        String raw = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        request.setTokenHash(hash(raw)); request.setExpiresAt(LocalDateTime.now().plusHours(1));
        request.setPreviousEmail(user.getEmail()); request.setRequestSessionVersion(user.getSessionVersion());
        request.setLastSentAt(LocalDateTime.now()); request.setLastSentBy(guard.currentUserId()); request.setSendCount(request.getSendCount()+1);
        request.setDeliveryStatus("PENDING");
        requests.saveAndFlush(request);
        try {
            mail.sendText(request.getNewEmail(), "Récupérez votre compte FasoÉcole",
                "Votre identité a été vérifiée par l’établissement ou le support. Pour confirmer cette adresse et choisir un nouveau mot de passe, ouvrez ce lien, valable une heure :\n\n"
                + frontendBaseUrl.replaceAll("/+$", "") + "/login?recover=" + raw
                + "\n\nVotre compte et ses dossiers seront conservés. Si vous n’avez pas demandé cette récupération, ignorez ce message.");
            request.setDeliveryStatus("SENT");
        } catch (MailException ex) { request.setDeliveryStatus("FAILED"); }
        if (user.getEmail() != null && !user.getEmail().isBlank()) {
            try {
                mail.sendText(user.getEmail(), "Demande de récupération de votre compte FasoÉcole",
                    "Une récupération de votre compte a été autorisée après vérification d’identité. Votre adresse et votre mot de passe ne changent qu’à la confirmation du lien envoyé à la nouvelle adresse. Si cette demande ne vient pas de vous, contactez immédiatement votre établissement ou le support technique.");
                request.setOldNotificationStatus("SENT");
            } catch (MailException ex) { request.setOldNotificationStatus("FAILED"); }
        } else request.setOldNotificationStatus("UNAVAILABLE");
        audit(request, guard.currentUserId(), "LINK_" + request.getDeliveryStatus(),
            ("SENT".equals(request.getDeliveryStatus()) ? "Lien envoyé à la nouvelle adresse. " : "Échec de l’envoi à la nouvelle adresse. ")
            + ("SENT".equals(request.getOldNotificationStatus()) ? "Ancienne adresse informée." : "Notification de l’ancienne adresse indisponible ou échouée."));
    }

    private boolean ownerCanRecover(AccountRecoveryRequest request, Long ownerId) {
        User user = request.getUser();
        if (ownerId == null || !owns(request.getSchool(), ownerId) || user.isOwnerAccount()
                || !user.getPlatformRoles().isEmpty() || !schools.findByOwnerId(user.getId()).isEmpty()
                || parents.findByUserId(user.getId()).isPresent() || !teachers.findByUserId(user.getId()).isEmpty()) return false;
        return memberships.findByUserId(user.getId()).stream().allMatch(link -> "STUDENT".equals(link.getRole().getName()) && owns(link.getSchool(), ownerId))
                && students.findAllByUserId(user.getId()).stream().allMatch(student -> owns(student.getSchool(), ownerId));
    }

    private boolean owns(School school, Long ownerId) {
        return school != null && school.getOwner() != null && ownerId.equals(school.getOwner().getId());
    }
    private void requireCurrentDossier(AccountRecoveryRequest request, User user) {
        if (request.getStudent() == null) throw invalidLink();
        var student = students.findById(request.getStudent().getId()).orElseThrow(this::invalidLink);
        if (!Boolean.TRUE.equals(user.getActive()) || !student.getUser().getId().equals(user.getId())
                || !student.getSchool().getId().equals(request.getSchool().getId())) throw invalidLink();
    }
    private void requireAvailableEmail(AccountRecoveryRequest request, User user) {
        if (users.findByEmailIgnoreCase(request.getNewEmail()).filter(other -> !other.getId().equals(user.getId())).isPresent())
            throw new IllegalArgumentException("La nouvelle adresse appartient déjà à un autre compte. Demandez une nouvelle demande avec une autre adresse ; aucun compte n’a été fusionné.");
    }
    private AccountRecoveryRequest requireRequest(Long id) {
        return requests.lockById(id).orElseThrow(() -> new IllegalArgumentException("Demande introuvable"));
    }
    private void authorize(AccountRecoveryRequest request) {
        if (!guard.isSuperAdmin() && !owns(request.getSchool(), guard.currentUserId()))
            throw new AccessDeniedException("Cette demande ne concerne pas votre établissement.");
    }
    private void recordDecision(AccountRecoveryRequest request, String note) {
        request.setDecidedBy(guard.currentUserId()); request.setDecidedAt(LocalDateTime.now());
        request.setSupportDecision(guard.isSuperAdmin()); request.setVerificationNote(note.trim());
    }
    private IllegalArgumentException invalidLink() { return new IllegalArgumentException("Lien de récupération invalide, expiré ou compte modifié. Contactez l’établissement pour une nouvelle vérification."); }
    private String normalize(String value) { return Normalizer.normalize(Objects.toString(value, ""), Normalizer.Form.NFD).replaceAll("\\p{M}", "").trim().replaceAll("\\s+", " ").toLowerCase(Locale.ROOT); }
    private String hash(String value) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8))); }
        catch (NoSuchAlgorithmException ex) { throw new IllegalStateException(ex); }
    }
    private View view(AccountRecoveryRequest r) {
        return new View(r.getId(),r.getSchool().getId(),r.getSchool().getName(),r.getStudent() == null ? null : r.getStudent().getId(),r.getFirstName(),r.getLastName(),r.getRegistrationNumber(),
            r.getNewEmail(),r.getReason(),r.getStatus().name(),r.getCreatedAt(),r.getDecidedAt(),r.getCompletedAt(),r.getDecidedBy(),
            r.getVerificationNote(),r.getDeliveryStatus(),r.getOldNotificationStatus(),r.getExpiresAt(),r.getSendCount(),
            students.findAllByUserId(r.getUser().getId()).stream().map(student -> student.getSchool().getName()).distinct().sorted().toList(),
            events.findByRequestIdOrderByCreatedAtAsc(r.getId()).stream().map(e -> new Audit(e.getActorId(), e.getAction(), e.getDetails(), e.getCreatedAt())).toList());
    }
    private void audit(AccountRecoveryRequest request, Long actor, String action, String details) {
        events.save(AccountRecoveryEvent.builder().request(request).actorId(actor).action(action).details(details.trim()).createdAt(LocalDateTime.now()).build());
    }
}
