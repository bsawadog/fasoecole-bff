package org.afritechinnovations.service.common;

import lombok.RequiredArgsConstructor;
import org.afritechinnovations.dto.common.CreateSchoolAccessRequest;
import org.afritechinnovations.dto.common.SchoolAccessRequestDto;
import org.afritechinnovations.model.common.Role;
import org.afritechinnovations.model.common.RoleName;
import org.afritechinnovations.model.common.School;
import org.afritechinnovations.model.common.SchoolAccessRequest;
import org.afritechinnovations.model.common.SchoolAccessStatus;
import org.afritechinnovations.model.common.SchoolStatus;
import org.afritechinnovations.model.common.SchoolUser;
import org.afritechinnovations.model.common.User;
import org.afritechinnovations.model.people.ParentStudent;
import org.afritechinnovations.repository.people.ParentRepository;
import org.afritechinnovations.repository.people.ParentStudentRepository;
import org.afritechinnovations.repository.common.RoleRepository;
import org.afritechinnovations.repository.common.SchoolAccessRequestRepository;
import org.afritechinnovations.repository.common.SchoolRepository;
import org.afritechinnovations.repository.common.SchoolUserRepository;
import org.afritechinnovations.repository.common.UserRepository;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/** Demandes d'accès d'un enseignant, parent ou élève déjà validé à un établissement supplémentaire. */
@Service
@RequiredArgsConstructor
@Transactional
public class SchoolAccessRequestService {

    private static final Set<RoleName> REQUESTABLE_ROLES = Set.of(RoleName.TEACHER, RoleName.PARENT, RoleName.STUDENT);

    private final SchoolAccessRequestRepository requestRepository;
    private final UserRepository userRepository;
    private final SchoolRepository schoolRepository;
    private final SchoolUserRepository schoolUserRepository;
    private final RoleRepository roleRepository;
    private final ParentChildAdmissionService parentChildAdmission;
    private final SchoolIdentityAdmissionService identityAdmission;
    private final ParentRepository parentRepository;
    private final ParentStudentRepository parentStudentRepository;

    @Transactional(readOnly = true)
    public List<SchoolAccessRequestDto> findMine(Long userId) {
        return requestRepository.findByUserIdOrderByCreatedAtDesc(userId).stream().map(this::toDto).toList();
    }

    public SchoolAccessRequestDto create(Long userId, CreateSchoolAccessRequest request) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("Utilisateur introuvable: " + userId));
        if (!Boolean.TRUE.equals(user.getActive()) || !Boolean.TRUE.equals(user.getApproved()) || !Boolean.TRUE.equals(user.getEmailVerified()) || !Boolean.TRUE.equals(user.getPasswordSet())) {
            throw new AccessDeniedException("Votre compte doit d'abord être validé par votre établissement");
        }
        List<SchoolUser> links = schoolUserRepository.findByUserId(userId);
        boolean eligible = links.stream()
                .map(link -> link.getRole().getName())
                .anyMatch(name -> REQUESTABLE_ROLES.stream().anyMatch(role -> role.name().equals(name)));
        if (!eligible) {
            throw new AccessDeniedException("Seuls les enseignants, parents et élèves peuvent demander l'accès à un autre établissement");
        }
        RoleName role = request.getRequestedRole();
        if (!REQUESTABLE_ROLES.contains(role)) {
            throw new IllegalArgumentException("Le profil demandé doit être enseignant, parent ou élève");
        }
        var identifier = SchoolIdentityAdmissionService.normalize(role, request.getSchoolIdentifier());
        var childNumbers = ParentChildAdmissionService.normalize(request.getChildRegistrationNumbers(), role == RoleName.PARENT);
        School school = schoolRepository.findById(request.getSchoolId())
                .filter(s -> s.getStatus() == SchoolStatus.ACTIVE)
                .orElseThrow(() -> new IllegalArgumentException("Établissement sélectionné introuvable ou inactif"));
        boolean alreadyLinked = links.stream().anyMatch(link -> link.getSchool().getId().equals(school.getId())
                && role.name().equals(link.getRole().getName()));
        if (alreadyLinked) {
            throw new IllegalArgumentException("Vous avez déjà accès à cet établissement avec ce profil");
        }
        if (requestRepository.existsByUserIdAndSchoolIdAndRequestedRoleAndStatus(
                userId, school.getId(), role, SchoolAccessStatus.PENDING)) {
            throw new IllegalArgumentException("Une demande est déjà en attente pour cet établissement et ce profil");
        }
        return toDto(requestRepository.save(SchoolAccessRequest.builder()
                .user(user)
                .school(school)
                .schoolIdentifier(identifier)
                .requestedRole(role)
                .childRegistrationNumbers(role == RoleName.PARENT ? new java.util.ArrayList<>(childNumbers) : new java.util.ArrayList<>())
                .build()));
    }

    public void cancel(Long userId, Long requestId) {
        SchoolAccessRequest request = findPending(requestId);
        if (!request.getUser().getId().equals(userId)) {
            throw new AccessDeniedException("Vous ne pouvez annuler que vos propres demandes");
        }
        requestRepository.delete(request);
    }

    @Transactional(readOnly = true)
    public List<SchoolAccessRequestDto> findPendingFor(Long approverId, boolean systemAdmin) {
        List<SchoolAccessStatus> statuses = List.of(SchoolAccessStatus.PENDING, SchoolAccessStatus.AUTO_APPROVED);
        List<SchoolAccessRequest> pending;
        if (systemAdmin) {
            pending = requestRepository.findByStatusInOrderByCreatedAtAsc(statuses);
        } else {
            List<Long> schoolIds = schoolRepository.findByOwnerId(approverId).stream().map(School::getId).toList();
            pending = schoolIds.isEmpty()
                    ? List.of()
                    : requestRepository.findByStatusInAndSchoolIdInOrderByCreatedAtAsc(statuses, schoolIds);
        }
        return pending.stream().map(request -> {
            var dto = toDto(request);
            if (request.getRequestedRole() == RoleName.PARENT) dto.setChildReview(parentChildAdmission.review(request.getSchool().getId(), request.getChildRegistrationNumbers()));
            if (request.getRequestedRole() == RoleName.STUDENT || request.getRequestedRole() == RoleName.TEACHER) dto.setIdentifierReview(identityAdmission.review(request.getSchool().getId(), request.getRequestedRole(), request.getSchoolIdentifier()));
            return dto;
        }).toList();
    }

    /** Le propriétaire valide un accès accordé automatiquement : il disparaît de la liste à traiter. */
    public SchoolAccessRequestDto confirmAutomatic(Long requestId, Long approverId, boolean systemAdmin) {
        SchoolAccessRequest request = findAutomatic(requestId);
        requireOwner(request.getSchool(), approverId, systemAdmin);
        return decide(request, SchoolAccessStatus.APPROVED, approverId);
    }

    /** Le propriétaire retire un accès accordé automatiquement ; il ne sera plus réaccordé par le système. */
    public SchoolAccessRequestDto revokeAutomatic(Long requestId, Long approverId, boolean systemAdmin) {
        SchoolAccessRequest request = findAutomatic(requestId);
        requireOwner(request.getSchool(), approverId, systemAdmin);
        String roleName = request.getRequestedRole().name();
        schoolUserRepository.findByUserId(request.getUser().getId()).stream()
                .filter(link -> link.getSchool().getId().equals(request.getSchool().getId())
                        && roleName.equals(link.getRole().getName()))
                .forEach(schoolUserRepository::delete);
        return decide(request, SchoolAccessStatus.REVOKED, approverId);
    }

    private SchoolAccessRequest findAutomatic(Long requestId) {
        SchoolAccessRequest request = requestRepository.findById(requestId)
                .orElseThrow(() -> new IllegalArgumentException("Demande d'accès introuvable"));
        if (request.getStatus() != SchoolAccessStatus.AUTO_APPROVED) {
            throw new IllegalArgumentException("Cet accès n'a pas été accordé automatiquement ou a déjà été traité");
        }
        return request;
    }

    public SchoolAccessRequestDto approve(Long requestId, Long approverId, boolean systemAdmin) {
        return approve(requestId, approverId, systemAdmin, null, null);
    }

    public SchoolAccessRequestDto approve(Long requestId, Long approverId, boolean systemAdmin, Long classId, String registrationNumber) {
        SchoolAccessRequest request = findPending(requestId);
        org.afritechinnovations.security.RegistrationRoles.requireAllowed(request.getRequestedRole());
        if (!Boolean.TRUE.equals(request.getUser().getEmailVerified()) || !Boolean.TRUE.equals(request.getUser().getActive())
                || !Boolean.TRUE.equals(request.getUser().getApproved()) || !Boolean.TRUE.equals(request.getUser().getPasswordSet())) {
            throw new IllegalArgumentException("Le compte doit être actif, approuvé et son courriel confirmé avant l'approbation");
        }
        requireOwner(request.getSchool(), approverId, systemAdmin);
        if (request.getSchool().getStatus() != SchoolStatus.ACTIVE) {
            throw new IllegalArgumentException("Impossible d'accorder l'accès à un établissement inactif");
        }
        if (request.getRequestedRole() == RoleName.PARENT) {
            parentChildAdmission.attachApproved(request.getUser(), request.getSchool().getId(), request.getChildRegistrationNumbers());
        }
        if (request.getRequestedRole() == RoleName.TEACHER || request.getRequestedRole() == RoleName.STUDENT) {
            identityAdmission.attachApproved(request.getUser(), request.getSchool(), request.getRequestedRole(), request.getSchoolIdentifier());
        }
        String roleName = request.getRequestedRole().name();
        boolean alreadyLinked = schoolUserRepository.findByUserId(request.getUser().getId()).stream()
                .anyMatch(link -> link.getSchool().getId().equals(request.getSchool().getId())
                        && roleName.equals(link.getRole().getName()));
        if (!alreadyLinked) {
            Role role = roleRepository.findByName(roleName)
                    .orElseThrow(() -> new IllegalStateException("Rôle non configuré: " + roleName));
            schoolUserRepository.save(SchoolUser.builder()
                    .user(request.getUser())
                    .school(request.getSchool())
                    .role(role)
                    .build());
        }
        return decide(request, SchoolAccessStatus.APPROVED, approverId);
    }

    public SchoolAccessRequestDto reject(Long requestId, Long approverId, boolean systemAdmin) {
        SchoolAccessRequest request = findPending(requestId);
        requireOwner(request.getSchool(), approverId, systemAdmin);
        return decide(request, SchoolAccessStatus.REJECTED, approverId);
    }

    private SchoolAccessRequestDto decide(SchoolAccessRequest request, SchoolAccessStatus status, Long approverId) {
        request.setStatus(status);
        request.setDecidedAt(LocalDateTime.now());
        request.setDecidedBy(userRepository.getReferenceById(approverId));
        return toDto(requestRepository.save(request));
    }

    private SchoolAccessRequest findPending(Long requestId) {
        SchoolAccessRequest request = requestRepository.findById(requestId)
                .orElseThrow(() -> new IllegalArgumentException("Demande d'accès introuvable"));
        if (request.getStatus() != SchoolAccessStatus.PENDING) {
            throw new IllegalArgumentException("Cette demande a déjà été traitée");
        }
        return request;
    }

    private void requireOwner(School school, Long approverId, boolean systemAdmin) {
        if (!systemAdmin && (school.getOwner() == null || !Objects.equals(school.getOwner().getId(), approverId))) {
            throw new AccessDeniedException("Vous ne pouvez traiter que les demandes destinées à vos établissements");
        }
    }

    private SchoolAccessRequestDto toDto(SchoolAccessRequest request) {
        User user = request.getUser();
        School school = request.getSchool();
        return SchoolAccessRequestDto.builder()
                .childRegistrationNumbers(List.copyOf(request.getChildRegistrationNumbers()))
                .schoolIdentifier(request.getSchoolIdentifier())
                .id(request.getId())
                .userId(user.getId())
                .firstName(user.getFirstName())
                .lastName(user.getLastName())
                .email(user.getEmail())
                .phone(user.getPhone())
                .schoolId(school.getId())
                .schoolName(school.getName())
                .schoolType(school.getType())
                .requestedRole(request.getRequestedRole())
                .status(request.getStatus())
                .createdAt(request.getCreatedAt())
                .decidedAt(request.getDecidedAt())
                .children(request.getRequestedRole() == RoleName.PARENT ? childrenAt(user.getId(), school.getId()) : List.of())
                .build();
    }

    /** Enfants (inscrits dans l'établissement) qui justifient l'accès d'un parent. */
    private List<String> childrenAt(Long userId, Long schoolId) {
        return parentRepository.findByUserId(userId)
                .map(parent -> parentStudentRepository.findChildrenWithUserByParentId(parent.getId()).stream()
                        .map(ParentStudent::getStudent)
                        .filter(student -> student.getSchool() != null && schoolId.equals(student.getSchool().getId()))
                        .map(student -> student.getUser().getFirstName() + " " + student.getUser().getLastName())
                        .toList())
                .orElse(List.of());
    }
}
