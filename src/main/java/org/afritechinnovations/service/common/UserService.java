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
    private final org.afritechinnovations.service.people.TeacherProfileService teacherProfileService;
    private final org.afritechinnovations.repository.people.ParentRepository parentRepository;

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
        if (userRepository.existsByEmailIgnoreCase(request.getEmail())) {
            throw new IllegalArgumentException("Un utilisateur existe déjà avec cet email: " + request.getEmail());
        }
        User user = User.builder()
                .firstName(request.getFirstName())
                .lastName(request.getLastName())
                .email(request.getEmail())
                .passwordHash(passwordEncoder.encode(request.getPassword()))
                .phone(request.getPhone())
                .active(true)
                .build();
        return toDto(userRepository.save(user));
    }

    /**
     * Inscription avec le courriel d'un compte créé par une école (parent saisi à l'inscription d'un enfant) :
     * rien n'est modifié, un lien d'activation est envoyé à cette adresse.
     * @return vrai si le cas a été traité ainsi.
     */
    public boolean requestActivationOfSchoolCreatedAccount(RegisterUserRequest request) {
        String email = request.getEmail().trim().toLowerCase();
        User existing = userRepository.findByEmailIgnoreCase(email).orElse(null);
        if (existing == null || Boolean.TRUE.equals(existing.getPasswordSet()) || !Boolean.TRUE.equals(existing.getActive())) {
            return false;
        }
        Long schoolId = schoolRepository.findById(request.getSchoolId())
                .filter(school -> school.getStatus() == org.afritechinnovations.model.common.SchoolStatus.ACTIVE)
                .map(School::getId)
                .orElse(null);
        emailVerificationService.sendActivation(existing, schoolId, request.getRequestedRole());
        return true;
    }

    public UserDto registerPending(RegisterUserRequest request) {
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
                .requestedSchoolId(requestedSchool.getId())
                .requestedRole(request.getRequestedRole())
                .build();
        user = userRepository.save(user);
        emailVerificationService.sendVerification(user);
        return toDto(user);
    }

    /** Renvoie le lien de confirmation de l'adresse du compte connecté. */
    public void resendEmailVerification(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("Utilisateur introuvable: " + userId));
        if (Boolean.TRUE.equals(user.getEmailVerified())) {
            throw new IllegalArgumentException("Votre adresse courriel est déjà vérifiée");
        }
        emailVerificationService.sendVerification(user);
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
                .map(this::toDto)
                .toList();
    }

    public UserDto approvePendingUser(Long userId, Long schoolId, RoleName roleName,
                                     Long approverId, boolean systemAdmin) {
        if (roleName != RoleName.TEACHER && roleName != RoleName.PARENT && roleName != RoleName.STUDENT) {
            throw new IllegalArgumentException("Un propriétaire peut attribuer uniquement un rôle enseignant, parent ou étudiant");
        }
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("Demande d'inscription introuvable"));
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

        Role role = roleRepository.findByName(roleName.name())
                .orElseThrow(() -> new IllegalStateException("Rôle non configuré: " + roleName));
        schoolUserRepository.save(SchoolUser.builder()
                .user(user)
                .school(school)
                .role(role)
                .build());
        if (roleName == RoleName.TEACHER) {
            teacherProfileService.ensureProfile(user, school);
        }
        if (roleName == RoleName.PARENT && parentRepository.findByUserId(user.getId()).isEmpty()) {
            parentRepository.save(org.afritechinnovations.model.people.Parent.builder().user(user).build());
        }
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
        user.setFirstName(request.getFirstName().trim());
        user.setLastName(request.getLastName().trim());
        user.setPhone(request.getPhone() == null || request.getPhone().isBlank()
                ? null : request.getPhone().trim());
        return toDto(userRepository.save(user));
    }

    public void changePassword(Long id, String currentPassword, String newPassword) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Utilisateur introuvable: " + id));
        if (!passwordEncoder.matches(currentPassword, user.getPasswordHash())) {
            throw new IllegalArgumentException("Le mot de passe actuel est incorrect");
        }
        if (passwordEncoder.matches(newPassword, user.getPasswordHash())) {
            throw new IllegalArgumentException("Le nouveau mot de passe doit être différent de l'actuel");
        }
        user.setPasswordHash(passwordEncoder.encode(newPassword));
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
                .id(user.getId())
                .firstName(user.getFirstName())
                .lastName(user.getLastName())
                .email(user.getEmail())
                .phone(user.getPhone())
                .active(user.getActive())
                .approved(user.getApproved())
                .emailVerified(user.getEmailVerified())
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
