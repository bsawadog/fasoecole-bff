package org.afritechinnovations.service.common;

import lombok.RequiredArgsConstructor;
import org.afritechinnovations.model.common.Role;
import org.afritechinnovations.model.common.RoleName;
import org.afritechinnovations.model.common.School;
import org.afritechinnovations.model.common.SchoolAccessRequest;
import org.afritechinnovations.model.common.SchoolAccessStatus;
import org.afritechinnovations.model.common.SchoolStatus;
import org.afritechinnovations.model.common.SchoolUser;
import org.afritechinnovations.model.common.User;
import org.afritechinnovations.model.people.Parent;
import org.afritechinnovations.model.people.ParentStudent;
import org.afritechinnovations.repository.common.RoleRepository;
import org.afritechinnovations.repository.common.SchoolAccessRequestRepository;
import org.afritechinnovations.repository.common.SchoolRepository;
import org.afritechinnovations.repository.common.SchoolUserRepository;
import org.afritechinnovations.repository.common.UserRepository;
import org.afritechinnovations.repository.people.ParentRepository;
import org.afritechinnovations.repository.people.ParentStudentRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Accorde automatiquement l'accès « parent » aux établissements où les enfants rattachés au compte
 * (reconnu par son courriel) sont inscrits. Chaque accès ainsi accordé est tracé par une demande
 * {@link SchoolAccessStatus#AUTO_APPROVED} que le propriétaire peut confirmer ou retirer.
 * Un accès retiré ({@link SchoolAccessStatus#REVOKED}) n'est jamais réaccordé automatiquement.
 */
@Service
@RequiredArgsConstructor
@Transactional
public class ParentAutoAccessService {

    private static final Set<SchoolAccessStatus> TRACKED = Set.of(
            SchoolAccessStatus.AUTO_APPROVED, SchoolAccessStatus.APPROVED, SchoolAccessStatus.REVOKED);

    private final UserRepository userRepository;
    private final ParentRepository parentRepository;
    private final ParentStudentRepository parentStudentRepository;
    private final SchoolUserRepository schoolUserRepository;
    private final SchoolAccessRequestRepository requestRepository;
    private final SchoolRepository schoolRepository;
    private final RoleRepository roleRepository;

    /**
     * @param activation vrai quand le titulaire prend possession de son compte (création de compte,
     *                   premier mot de passe) : chaque établissement lié est alors signalé au propriétaire.
     * @return le nombre d'établissements nouvellement accordés ou signalés.
     */
    public int grantFromChildren(Long userId, boolean activation) {
        User user = userRepository.findById(userId).orElse(null);
        if (user == null || !Boolean.TRUE.equals(user.getActive()) || !Boolean.TRUE.equals(user.getEmailVerified())) {
            return 0;
        }
        Parent parent = parentRepository.findByUserId(userId).orElse(null);
        if (parent == null) {
            return 0;
        }
        Map<Long, School> schools = new LinkedHashMap<>();
        for (ParentStudent link : parentStudentRepository.findByParentId(parent.getId())) {
            School school = link.getStudent().getSchool();
            if (school != null && school.getStatus() == SchoolStatus.ACTIVE) {
                schools.putIfAbsent(school.getId(), school);
            }
        }
        if (schools.isEmpty()) {
            return 0;
        }

        boolean wasApproved = Boolean.TRUE.equals(user.getApproved());
        List<SchoolUser> links = schoolUserRepository.findByUserId(userId);
        Role parentRole = null;
        int granted = 0;
        boolean anyAccess = false;
        for (School school : schools.values()) {
            List<SchoolAccessRequest> history = requestRepository
                    .findByUserIdAndSchoolIdAndRequestedRole(userId, school.getId(), RoleName.PARENT);
            if (history.stream().anyMatch(r -> r.getStatus() == SchoolAccessStatus.REVOKED)) {
                continue;
            }
            anyAccess = true;
            boolean linked = links.stream().anyMatch(su -> su.getSchool().getId().equals(school.getId())
                    && RoleName.PARENT.name().equals(su.getRole().getName()));
            if (!linked) {
                if (parentRole == null) {
                    parentRole = roleRepository.findByName(RoleName.PARENT.name())
                            .orElseThrow(() -> new IllegalStateException("Rôle non configuré: PARENT"));
                }
                schoolUserRepository.save(SchoolUser.builder().user(user).school(school).role(parentRole).build());
            }
            boolean tracked = history.stream().anyMatch(r -> TRACKED.contains(r.getStatus()));
            if (tracked || (linked && !activation && wasApproved)) {
                continue;
            }
            SchoolAccessRequest pending = history.stream()
                    .filter(r -> r.getStatus() == SchoolAccessStatus.PENDING).findFirst().orElse(null);
            SchoolAccessRequest record = pending != null ? pending
                    : SchoolAccessRequest.builder().user(user).school(school).requestedRole(RoleName.PARENT).build();
            record.setStatus(SchoolAccessStatus.AUTO_APPROVED);
            record.setDecidedAt(LocalDateTime.now());
            record.setDecidedBy(null);
            requestRepository.save(record);
            granted++;
        }

        if (anyAccess && !wasApproved) {
            user.setApproved(true);
            userRepository.save(user);
            convertPendingRegistration(user);
        }
        return granted;
    }

    /** La demande d'inscription initiale (école/profil choisis) devient une demande d'accès classique. */
    private void convertPendingRegistration(User user) {
        requestSchool(user.getId(), user.getRequestedSchoolId(), user.getRequestedRole());
    }

    /** Crée une demande d'accès EN ATTENTE (validée par le propriétaire) si l'accès n'existe pas déjà. */
    public void requestSchool(Long userId, Long schoolId, RoleName role) {
        if (schoolId == null || role == null) {
            return;
        }
        org.afritechinnovations.security.RegistrationRoles.requireAllowed(role);
        User user = userRepository.findById(userId).orElse(null);
        if (user == null) {
            return;
        }
        boolean linked = schoolUserRepository.findByUserId(user.getId()).stream()
                .anyMatch(su -> su.getSchool().getId().equals(schoolId) && role.name().equals(su.getRole().getName()));
        boolean requested = requestRepository.findByUserIdAndSchoolIdAndRequestedRole(user.getId(), schoolId, role)
                .stream().anyMatch(r -> r.getStatus() == SchoolAccessStatus.PENDING);
        if (linked || requested) {
            return;
        }
        schoolRepository.findById(schoolId)
                .filter(s -> s.getStatus() == SchoolStatus.ACTIVE)
                .ifPresent(school -> requestRepository.save(SchoolAccessRequest.builder()
                        .user(user).school(school).requestedRole(role)
                        .schoolIdentifier(schoolId.equals(user.getRequestedSchoolId()) && role == user.getRequestedRole() ? user.getSchoolIdentifier() : null)
                        .childRegistrationNumbers(role == RoleName.PARENT && schoolId.equals(user.getRequestedSchoolId())
                                ? new java.util.ArrayList<>(user.getChildRegistrationNumbers()) : new java.util.ArrayList<>()).build()));
    }
}
