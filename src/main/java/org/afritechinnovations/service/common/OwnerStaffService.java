package org.afritechinnovations.service.common;

import lombok.RequiredArgsConstructor;
import org.afritechinnovations.dto.common.OwnerStaffDto;
import org.afritechinnovations.model.common.Role;
import org.afritechinnovations.model.common.School;
import org.afritechinnovations.model.common.SchoolStaff;
import org.afritechinnovations.model.common.SchoolUser;
import org.afritechinnovations.model.common.StaffModule;
import org.afritechinnovations.model.common.User;
import org.afritechinnovations.repository.common.RoleRepository;
import org.afritechinnovations.repository.common.SchoolRepository;
import org.afritechinnovations.repository.common.SchoolStaffRepository;
import org.afritechinnovations.repository.common.SchoolUserRepository;
import org.afritechinnovations.repository.common.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/** Personnel administratif d'un établissement et délégation des modules de l'espace propriétaire. */
@Service
@RequiredArgsConstructor
@Transactional
public class OwnerStaffService {

    public static final String STAFF_ROLE = "STAFF";
    private static final Logger log = LoggerFactory.getLogger(OwnerStaffService.class);
    private static final String PASSWORD_ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnpqrstuvwxyz23456789";
    private static final SecureRandom RANDOM = new SecureRandom();

    private final SchoolRepository schoolRepository;
    private final SchoolStaffRepository staffRepository;
    private final SchoolUserRepository schoolUserRepository;
    private final RoleRepository roleRepository;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final EmailService emailService;

    @Value("${app.frontend.base-url:http://localhost:4200}")
    private String frontendBaseUrl;

    // ---------------------------------------------------------------- accès de l'utilisateur courant

    /** Établissements accessibles dans l'espace propriétaire : ceux possédés puis ceux délégués. */
    @Transactional(readOnly = true)
    public List<OwnerStaffDto.SchoolAccess> accessOf(Long userId) {
        return accessOf(userId, false);
    }

    @Transactional(readOnly = true)
    public List<OwnerStaffDto.SchoolAccess> accessOf(Long userId, boolean systemAdmin) {
        Map<Long, OwnerStaffDto.SchoolAccess> access = new LinkedHashMap<>();
        List<String> allModules = Arrays.stream(StaffModule.values()).map(Enum::name).toList();
        (systemAdmin ? schoolRepository.findAll() : schoolRepository.findByOwnerId(userId)).forEach(school -> access.put(school.getId(),
                new OwnerStaffDto.SchoolAccess(school.getId(), school.getName(), typeOf(school), true,
                        "Propriétaire", allModules)));
        for (SchoolStaff staff : staffRepository.findByUserWithSchool(userId)) {
            School school = staff.getSchool();
            if (!staff.isActive() || staff.getModules().isEmpty() || access.containsKey(school.getId())) {
                continue;
            }
            access.put(school.getId(), new OwnerStaffDto.SchoolAccess(school.getId(), school.getName(),
                    typeOf(school), false, staff.getJobTitle(), sortedModules(staff.getModules())));
        }
        return List.copyOf(access.values());
    }

    // ---------------------------------------------------------------- gestion par le propriétaire

    @Transactional(readOnly = true)
    public List<OwnerStaffDto.StaffRow> list(Long schoolId, Long ownerId, boolean systemAdmin) {
        requireOwnedSchool(schoolId, ownerId, systemAdmin);
        return staffRepository.findBySchoolWithUser(schoolId).stream().map(this::toRow).toList();
    }

    public OwnerStaffDto.StaffCreated create(Long schoolId, OwnerStaffDto.StaffRequest request,
                                             Long ownerId, boolean systemAdmin) {
        School school = requireOwnedSchool(schoolId, ownerId, systemAdmin);
        String email = normalizeEmail(request.email());
        User user = userRepository.findByEmailIgnoreCase(email).orElse(null);
        boolean existingAccount = user != null;
        String temporaryPassword = null;

        if (existingAccount) {
            if (school.getOwner() != null && school.getOwner().getId().equals(user.getId())) {
                throw new IllegalArgumentException("Le propriétaire dispose déjà de tous les accès");
            }
            if (staffRepository.existsBySchoolIdAndUserId(schoolId, user.getId())) {
                throw new IllegalArgumentException("Cette personne fait déjà partie du personnel de l'établissement");
            }
        } else {
            temporaryPassword = generatePassword();
            user = userRepository.save(User.builder()
                    .firstName(request.firstName().trim())
                    .lastName(request.lastName().trim())
                    .email(email)
                    .phone(blankToNull(request.phone()))
                    .passwordHash(passwordEncoder.encode(temporaryPassword))
                    .active(true)
                    .approved(true)
                    .build());
        }

        ensureStaffRole(user, school);
        SchoolStaff staff = staffRepository.save(SchoolStaff.builder()
                .school(school)
                .user(user)
                .jobTitle(request.jobTitle().trim())
                .modules(EnumSet.copyOf(request.modules()))
                .createdBy(userRepository.getReferenceById(ownerId))
                .build());

        boolean emailSent = existingAccount
                ? sendQuietly(email, "Nouvel accès FasoÉcole – " + school.getName(), accessGrantedText(staff))
                : sendQuietly(email, "Votre compte FasoÉcole – " + school.getName(),
                        credentialsText(staff, temporaryPassword));
        return new OwnerStaffDto.StaffCreated(toRow(staff), temporaryPassword, existingAccount, emailSent);
    }

    public OwnerStaffDto.StaffRow update(Long staffId, OwnerStaffDto.StaffRequest request,
                                         Long ownerId, boolean systemAdmin) {
        SchoolStaff staff = requireOwnedStaff(staffId, ownerId, systemAdmin);
        staff.setJobTitle(request.jobTitle().trim());
        staff.getModules().clear();
        staff.getModules().addAll(request.modules());
        staff.setUpdatedAt(LocalDateTime.now());
        if (isManagedAccount(staff.getUser())) {
            User user = staff.getUser();
            user.setFirstName(request.firstName().trim());
            user.setLastName(request.lastName().trim());
            user.setPhone(blankToNull(request.phone()));
        }
        return toRow(staffRepository.save(staff));
    }

    public OwnerStaffDto.StaffRow setActive(Long staffId, boolean active, Long ownerId, boolean systemAdmin) {
        SchoolStaff staff = requireOwnedStaff(staffId, ownerId, systemAdmin);
        staff.setActive(active);
        staff.setUpdatedAt(LocalDateTime.now());
        return toRow(staffRepository.save(staff));
    }

    public OwnerStaffDto.PasswordReset resetPassword(Long staffId, Long ownerId, boolean systemAdmin) {
        SchoolStaff staff = requireOwnedStaff(staffId, ownerId, systemAdmin);
        User user = staff.getUser();
        if (!isManagedAccount(user)) {
            throw new IllegalArgumentException(
                    "Ce compte est aussi utilisé ailleurs sur la plateforme : la personne doit utiliser « Mot de passe oublié »");
        }
        String temporaryPassword = generatePassword();
        user.setPasswordHash(passwordEncoder.encode(temporaryPassword));
        userRepository.save(user);
        boolean emailSent = sendQuietly(user.getEmail(), "Nouveau mot de passe FasoÉcole",
                credentialsText(staff, temporaryPassword));
        return new OwnerStaffDto.PasswordReset(temporaryPassword, emailSent);
    }

    /** Retire l'accès : la fiche de délégation et le rôle STAFF de cet établissement sont supprimés. */
    public void remove(Long staffId, Long ownerId, boolean systemAdmin) {
        SchoolStaff staff = requireOwnedStaff(staffId, ownerId, systemAdmin);
        Long schoolId = staff.getSchool().getId();
        schoolUserRepository.findByUserId(staff.getUser().getId()).stream()
                .filter(link -> link.getSchool().getId().equals(schoolId)
                        && STAFF_ROLE.equals(link.getRole().getName()))
                .forEach(schoolUserRepository::delete);
        staffRepository.delete(staff);
    }

    // ---------------------------------------------------------------- interne

    private void ensureStaffRole(User user, School school) {
        boolean linked = schoolUserRepository.findByUserId(user.getId()).stream()
                .anyMatch(link -> link.getSchool().getId().equals(school.getId())
                        && STAFF_ROLE.equals(link.getRole().getName()));
        if (!linked) {
            Role role = roleRepository.findByName(STAFF_ROLE)
                    .orElseThrow(() -> new IllegalStateException("Rôle STAFF absent de la base"));
            schoolUserRepository.save(SchoolUser.builder().user(user).school(school).role(role).build());
        }
    }

    /** Compte créé pour le personnel et utilisé uniquement à ce titre : le propriétaire peut le gérer. */
    private boolean isManagedAccount(User user) {
        boolean onlyStaff = schoolUserRepository.findByUserId(user.getId()).stream()
                .allMatch(link -> STAFF_ROLE.equals(link.getRole().getName()));
        return onlyStaff && schoolRepository.findByOwnerId(user.getId()).isEmpty();
    }

    private OwnerStaffDto.StaffRow toRow(SchoolStaff staff) {
        User user = staff.getUser();
        return new OwnerStaffDto.StaffRow(staff.getId(), user.getId(), user.getFirstName(), user.getLastName(),
                user.getEmail(), user.getPhone(), staff.getJobTitle(), sortedModules(staff.getModules()),
                staff.isActive(), isManagedAccount(user), staff.getCreatedAt());
    }

    private static List<String> sortedModules(Set<StaffModule> modules) {
        return Arrays.stream(StaffModule.values()).filter(modules::contains).map(Enum::name).toList();
    }

    private School requireOwnedSchool(Long schoolId, Long ownerId, boolean systemAdmin) {
        School school = schoolRepository.findById(schoolId)
                .orElseThrow(() -> new IllegalArgumentException("Établissement introuvable : " + schoolId));
        if (!systemAdmin && (school.getOwner() == null || !school.getOwner().getId().equals(ownerId))) {
            throw new AccessDeniedException("Seul le propriétaire peut gérer le personnel de l'établissement");
        }
        return school;
    }

    private SchoolStaff requireOwnedStaff(Long staffId, Long ownerId, boolean systemAdmin) {
        SchoolStaff staff = staffRepository.findById(staffId)
                .orElseThrow(() -> new IllegalArgumentException("Membre du personnel introuvable : " + staffId));
        requireOwnedSchool(staff.getSchool().getId(), ownerId, systemAdmin);
        return staff;
    }

    private boolean sendQuietly(String recipient, String subject, String body) {
        try {
            emailService.sendText(recipient, subject, body);
            return true;
        } catch (RuntimeException e) {
            log.warn("E-mail au personnel non envoyé à {} : {}", recipient, e.getMessage());
            return false;
        }
    }

    private String credentialsText(SchoolStaff staff, String password) {
        return """
                Bonjour %s,

                Un accès à l'espace de gestion de « %s » vous a été ouvert sur FasoÉcole (%s).

                Adresse de connexion : %s
                Identifiant : %s
                Mot de passe provisoire : %s

                Modules autorisés : %s

                Pensez à changer ce mot de passe après votre première connexion (« Mot de passe oublié »).
                """.formatted(staff.getUser().getFirstName(), staff.getSchool().getName(), staff.getJobTitle(),
                loginUrl(), staff.getUser().getEmail(), password, moduleLabels(staff));
    }

    private String accessGrantedText(SchoolStaff staff) {
        return """
                Bonjour %s,

                Un accès à l'espace de gestion de « %s » vous a été ouvert sur FasoÉcole (%s).
                Connectez-vous avec votre compte habituel : %s

                Modules autorisés : %s
                """.formatted(staff.getUser().getFirstName(), staff.getSchool().getName(), staff.getJobTitle(),
                loginUrl(), moduleLabels(staff));
    }

    private String loginUrl() {
        String base = frontendBaseUrl == null ? "http://localhost:4200" : frontendBaseUrl;
        return base.replaceAll("/+$", "") + "/login";
    }

    private static String moduleLabels(SchoolStaff staff) {
        return String.join(", ", Arrays.stream(StaffModule.values())
                .filter(staff.getModules()::contains).map(StaffModule::getLabel).toList());
    }

    private static String generatePassword() {
        StringBuilder password = new StringBuilder(10);
        for (int i = 0; i < 10; i++) {
            password.append(PASSWORD_ALPHABET.charAt(RANDOM.nextInt(PASSWORD_ALPHABET.length())));
        }
        return password.toString();
    }

    private static String normalizeEmail(String email) {
        if (email == null || email.isBlank()) {
            throw new IllegalArgumentException("L'adresse e-mail est obligatoire");
        }
        return email.trim().toLowerCase(Locale.ROOT);
    }

    private static String typeOf(School school) {
        return school.getType() == null ? null : school.getType().name();
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
