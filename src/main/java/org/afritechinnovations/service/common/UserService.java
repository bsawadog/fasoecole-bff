package org.afritechinnovations.service.common;

import lombok.RequiredArgsConstructor;
import org.afritechinnovations.dto.common.CreateUserRequest;
import org.afritechinnovations.dto.common.UserDto;
import org.afritechinnovations.dto.common.UpdateProfileRequest;
import org.afritechinnovations.dto.auth.RegisterUserRequest;
import org.afritechinnovations.model.common.Role;
import org.afritechinnovations.model.common.RoleName;
import org.afritechinnovations.model.common.School;
import org.afritechinnovations.model.common.SchoolUser;
import org.afritechinnovations.model.common.User;
import org.afritechinnovations.repository.common.RoleRepository;
import org.afritechinnovations.repository.common.SchoolRepository;
import org.afritechinnovations.repository.common.SchoolUserRepository;
import org.afritechinnovations.repository.common.UserRepository;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional
public class UserService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final SchoolRepository schoolRepository;
    private final RoleRepository roleRepository;
    private final SchoolUserRepository schoolUserRepository;
    private final org.afritechinnovations.service.auth.EmailVerificationService emailVerificationService;
    private final AccountOnboardingService onboardingService;
    private final ParentChildAdmissionService parentChildAdmission;
    private final SchoolIdentityAdmissionService identityAdmission;

    public List<UserDto> findActive() {
        return userRepository.findByActiveTrueAndApprovedTrueOrderByLastNameAsc()
                .stream()
                .map(this::toDto)
                .toList();
    }

    public UserDto findById(Long id) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Utilisateur introuvable: " + id));
        return toDto(user);
    }

    public UserDto findByEmail(String email) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new IllegalArgumentException("Utilisateur introuvable: " + email));
        return toDto(user);
    }

    public UserDto create(CreateUserRequest request) {
        String email = request.getEmail().trim().toLowerCase(java.util.Locale.ROOT);
        if (userRepository.existsByEmailIgnoreCase(email)) {
            throw new IllegalArgumentException("Un utilisateur existe déjà avec cet email: " + request.getEmail());
        }
        User user = User.builder()
                .firstName(request.getFirstName().trim())
                .lastName(request.getLastName().trim())
                .email(email)
                .passwordHash(passwordEncoder.encode(java.util.UUID.randomUUID().toString()))
                .passwordSet(false)
                .phone(request.getPhone())
                .active(true)
                .build();
        user = userRepository.save(user);
        emailVerificationService.sendInvitation(user);
        return toDto(user);
    }

    /**
     * Inscription avec le courriel d'un compte créé par une école (parent saisi à l'inscription d'un enfant) :
     * rien n'est modifié, un lien d'activation est envoyé à cette adresse.
     * @return vrai si le cas a été traité ainsi.
     */
    public boolean requestActivationOfSchoolCreatedAccount(RegisterUserRequest request) {
        var identifier = SchoolIdentityAdmissionService.normalize(request.getRequestedRole(), request.getSchoolIdentifier());
        org.afritechinnovations.security.RegistrationRoles.requireAllowed(request.getRequestedRole());
        java.util.List<String> childNumbers = ParentChildAdmissionService.normalize(request.getChildRegistrationNumbers(), request.getRequestedRole() == RoleName.PARENT);
        String email = request.getEmail().trim().toLowerCase();
        User existing = userRepository.findByEmailIgnoreCase(email).orElse(null);
        if (existing == null || Boolean.TRUE.equals(existing.getPasswordSet()) || !Boolean.TRUE.equals(existing.getActive())) {
            return false;
        }
        Long schoolId = schoolRepository.findById(request.getSchoolId())
                .filter(school -> school.getStatus() == org.afritechinnovations.model.common.SchoolStatus.ACTIVE)
                .map(School::getId)
                .orElse(null);
        if (schoolId == null) throw new IllegalArgumentException("Établissement sélectionné introuvable ou inactif");
        emailVerificationService.sendActivation(existing, schoolId, request.getRequestedRole(), childNumbers, identifier);
        return true;
    }

    public UserDto registerPending(RegisterUserRequest request) {
        var identifier = SchoolIdentityAdmissionService.normalize(request.getRequestedRole(), request.getSchoolIdentifier());
        java.util.List<String> childNumbers = ParentChildAdmissionService.normalize(request.getChildRegistrationNumbers(), request.getRequestedRole() == RoleName.PARENT);
        String email = request.getEmail().trim().toLowerCase();
        if (request.getRequestedRole() != RoleName.TEACHER
                && request.getRequestedRole() != RoleName.PARENT
                && request.getRequestedRole() != RoleName.STUDENT) {
            throw new IllegalArgumentException("Le profil demandé doit être enseignant, parent ou étudiant");
        }
        User existing = userRepository.findByEmailIgnoreCase(email).orElse(null);
        if (existing != null) {
            throw new IllegalArgumentException("Un utilisateur existe déjà avec cet email: " + email);
        }

        School requestedSchool = schoolRepository.findById(request.getSchoolId())
                .filter(school -> school.getStatus() == org.afritechinnovations.model.common.SchoolStatus.ACTIVE)
                .orElseThrow(() -> new IllegalArgumentException("Établissement sélectionné introuvable ou inactif"));
        User user = User.builder()
                .firstName(request.getFirstName().trim())
                .lastName(request.getLastName().trim())
                .email(email)
                .passwordHash(passwordEncoder.encode(request.getPassword()))
                .phone(request.getPhone())
                .active(true)
                .approved(false)
                .schoolIdentifier(identifier)
                .requestedSchoolId(requestedSchool.getId())
                .childRegistrationNumbers(request.getRequestedRole() == RoleName.PARENT ? new java.util.ArrayList<>(childNumbers) : new java.util.ArrayList<>())
                .requestedRole(request.getRequestedRole())
                .build();
        user = userRepository.save(user);
        emailVerificationService.sendVerification(user);
        return toDto(user);
    }

    /** Réponse publique identique : ne révèle ni l'existence ni l'état du compte. */
    public void receiveRegistration(RegisterUserRequest request) {
        var identifier = SchoolIdentityAdmissionService.normalize(request.getRequestedRole(), request.getSchoolIdentifier());
        var childNumbers = ParentChildAdmissionService.normalize(request.getChildRegistrationNumbers(), request.getRequestedRole() == RoleName.PARENT);
        org.afritechinnovations.security.RegistrationRoles.requireAllowed(request.getRequestedRole());
        schoolRepository.findById(request.getSchoolId())
                .filter(s -> s.getStatus() == org.afritechinnovations.model.common.SchoolStatus.ACTIVE)
                .orElseThrow(() -> new IllegalArgumentException("Établissement sélectionné introuvable ou inactif"));
        User existing = userRepository.findByEmailIgnoreCase(request.getEmail().trim().toLowerCase()).orElse(null);
        if (existing == null) registerPending(request);
        else if (Boolean.TRUE.equals(existing.getActive())) {
            if (!Boolean.TRUE.equals(existing.getPasswordSet())) emailVerificationService.sendActivation(existing, request.getSchoolId(), request.getRequestedRole(), childNumbers, identifier);
            else if (!Boolean.TRUE.equals(existing.getEmailVerified())) emailVerificationService.sendVerification(existing);
        }
    }

    /** Renvoie le lien de confirmation de l'adresse du compte connecté. */
    public boolean resendEmailVerification(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("Utilisateur introuvable: " + userId));
        if (Boolean.TRUE.equals(user.getEmailVerified())) {
            throw new IllegalArgumentException("Votre adresse courriel est déjà vérifiée");
        }
        return emailVerificationService.sendInvitation(user);
    }

    public boolean resendInvitation(Long userId) {
        User user = userRepository.findById(userId).orElseThrow(() -> new IllegalArgumentException("Compte introuvable"));
        if (!Boolean.TRUE.equals(user.getActive())) throw new IllegalArgumentException("Compte inactif");
        return emailVerificationService.sendInvitation(user);
    }

    public List<UserDto> findPendingApprovals(Long approverId, boolean systemAdmin) {
        List<User> pendingUsers;
        if (systemAdmin) {
            pendingUsers = userRepository.findByApprovedFalseOrderByCreatedAtAsc();
        } else {
            List<Long> schoolIds = schoolRepository.findByOwnerId(approverId).stream()
                    .map(School::getId)
                    .toList();
            pendingUsers = schoolIds.isEmpty()
                    ? List.of()
                    : userRepository.findByApprovedFalseAndRequestedSchoolIdInOrderByCreatedAtAsc(schoolIds);
        }
        return pendingUsers
                .stream()
                .map(user -> {
                    UserDto dto = toDto(user);
                    if (user.getRequestedRole() == RoleName.PARENT && user.getRequestedSchoolId() != null) {
                        dto.setChildReview(parentChildAdmission.review(user.getRequestedSchoolId(), user.getChildRegistrationNumbers()));
                    }
                    if ((user.getRequestedRole() == RoleName.STUDENT || user.getRequestedRole() == RoleName.TEACHER) && user.getRequestedSchoolId() != null) {
                        dto.setIdentifierReview(identityAdmission.review(user.getRequestedSchoolId(), user.getRequestedRole(), user.getSchoolIdentifier()));
                    }
                    return dto;
                })
                .toList();
    }

    public UserDto approvePendingUser(Long userId, Long schoolId, RoleName roleName,
                                     Long approverId, boolean systemAdmin) {
        return approvePendingUser(userId, schoolId, roleName, approverId, systemAdmin, null, null);
    }

    public UserDto approvePendingUser(Long userId, Long schoolId, RoleName roleName,
                                     Long approverId, boolean systemAdmin, Long classId, String registrationNumber) {
        if (roleName != RoleName.TEACHER && roleName != RoleName.PARENT && roleName != RoleName.STUDENT) {
            throw new IllegalArgumentException("Un propriétaire peut attribuer uniquement un rôle enseignant, parent ou étudiant");
        }
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("Demande d'inscription introuvable"));
        if (!Boolean.TRUE.equals(user.getActive()) || !Boolean.TRUE.equals(user.getEmailVerified()) || !Boolean.TRUE.equals(user.getPasswordSet())) {
            throw new IllegalArgumentException("Le titulaire doit confirmer son courriel et choisir son mot de passe avant l'approbation");
        }
        if (Boolean.TRUE.equals(user.getApproved())) {
            throw new IllegalArgumentException("Cette demande a déjà été traitée");
        }

        School school = schoolRepository.findById(schoolId)
                .orElseThrow(() -> new IllegalArgumentException("Établissement introuvable"));
        if (school.getStatus() != org.afritechinnovations.model.common.SchoolStatus.ACTIVE) {
            throw new IllegalArgumentException("Impossible d'activer un compte dans un établissement inactif");
        }
        if (!systemAdmin) {
            boolean ownsSelectedSchool = school.getOwner().getId().equals(approverId);
            boolean ownsRequestedSchool = user.getRequestedSchoolId() != null
                    && schoolRepository.findById(user.getRequestedSchoolId())
                    .map(requestedSchool -> requestedSchool.getOwner().getId().equals(approverId))
                    .orElse(false);
            if (!ownsSelectedSchool || !ownsRequestedSchool) {
                throw new AccessDeniedException("Vous ne pouvez valider que les demandes destinées à votre établissement");
            }
        }

        if (roleName == RoleName.PARENT) {
            if (!java.util.Objects.equals(schoolId, user.getRequestedSchoolId())) {
                throw new IllegalArgumentException("Les matricules doivent être vérifiés dans l'établissement demandé");
            }
            parentChildAdmission.attachApproved(user, schoolId, user.getChildRegistrationNumbers());
        }
        if (roleName == RoleName.TEACHER || roleName == RoleName.STUDENT) {
            if (roleName != user.getRequestedRole() || !java.util.Objects.equals(schoolId, user.getRequestedSchoolId())) {
                throw new IllegalArgumentException("Le profil et l’établissement doivent correspondre à l’identifiant déclaré");
            }
            identityAdmission.attachApproved(user, school, roleName, user.getSchoolIdentifier());
        }
        Role role = roleRepository.findByName(roleName.name())
                .orElseThrow(() -> new IllegalStateException("Rôle non configuré: " + roleName));
        schoolUserRepository.save(SchoolUser.builder()
                .user(user)
                .school(school)
                .role(role)
                .build());
        user.setApproved(true);
        user.setActive(true);
        return toDto(userRepository.save(user));
    }

    public UserDto update(Long id, UserDto dto) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Utilisateur introuvable: " + id));
        user.setFirstName(dto.getFirstName());
        user.setLastName(dto.getLastName());
        user.setPhone(dto.getPhone());
        return toDto(userRepository.save(user));
    }

    public UserDto updateProfile(Long id, UpdateProfileRequest request) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Utilisateur introuvable: " + id));
        if (request.getChildRegistrationNumbers() != null) {
            if (Boolean.TRUE.equals(user.getApproved()) || user.getRequestedRole() != RoleName.PARENT) {
                throw new IllegalArgumentException("Les matricules ne peuvent être corrigés que sur une inscription parent en attente");
            }
            user.setChildRegistrationNumbers(new java.util.ArrayList<>(ParentChildAdmissionService.normalize(request.getChildRegistrationNumbers(), true)));
        }
        if (request.getSchoolIdentifier() != null) {
            if (Boolean.TRUE.equals(user.getApproved())) throw new IllegalArgumentException("Le compte est déjà approuvé");
            user.setSchoolIdentifier(SchoolIdentityAdmissionService.normalize(user.getRequestedRole(), request.getSchoolIdentifier()));
        }
        user.setFirstName(request.getFirstName().trim());
        user.setLastName(request.getLastName().trim());
        user.setPhone(request.getPhone() == null || request.getPhone().isBlank()
                ? null : request.getPhone().trim());
        return toDto(userRepository.save(user));
    }

    public void changePassword(Long id, String currentPassword, String newPassword) {
        if (newPassword == null || newPassword.length() < 8 || newPassword.length() > 100) {
            throw new IllegalArgumentException("Le mot de passe doit contenir de 8 à 100 caractères");
        }
        User user = userRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Utilisateur introuvable: " + id));
        if (!passwordEncoder.matches(currentPassword, user.getPasswordHash())) {
            throw new IllegalArgumentException("Le mot de passe actuel est incorrect");
        }
        if (passwordEncoder.matches(newPassword, user.getPasswordHash())) {
            throw new IllegalArgumentException("Le nouveau mot de passe doit être différent de l'actuel");
        }
        user.setPasswordHash(passwordEncoder.encode(newPassword));
        user.revokeSessions();
        userRepository.save(user);
    }

    public void deactivate(Long id) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Utilisateur introuvable: " + id));
        user.setActive(false);
        userRepository.save(user);
    }

    public void delete(Long id) {
        userRepository.deleteById(id);
    }

    private UserDto toDto(User user) {
        School requestedSchool = user.getRequestedSchoolId() == null
                ? null
                : schoolRepository.findById(user.getRequestedSchoolId()).orElse(null);
        return UserDto.builder()
                .childRegistrationNumbers(List.copyOf(user.getChildRegistrationNumbers()))
                .schoolIdentifier(user.getSchoolIdentifier())
                .id(user.getId())
                .firstName(user.getFirstName())
                .lastName(user.getLastName())
                .email(user.getEmail())
                .phone(user.getPhone())
                .active(user.getActive())
                .approved(user.getApproved())
                .emailVerified(user.getEmailVerified())
                .passwordSet(user.getPasswordSet())
                .invitationDeliveryStatus(emailVerificationService.deliveryStatus(user.getId()))
                .onboardingSteps(onboardingService.steps(user))
                .requestedSchoolId(user.getRequestedSchoolId())
                .requestedSchoolName(requestedSchool == null ? null : requestedSchool.getName())
                .requestedSchoolType(requestedSchool == null ? null : requestedSchool.getType())
                .requestedRole(user.getRequestedRole())
                .roles(schoolUserRepository.findByUserId(user.getId()).stream()
                        .map(SchoolUser::getRole)
                        .map(Role::getName)
                        .distinct()
                        .toList())
                .createdAt(user.getCreatedAt())
                .updatedAt(user.getUpdatedAt())
                .build();
    }
}
