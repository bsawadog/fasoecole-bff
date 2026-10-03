package org.afritechinnovations.service.common;

import lombok.RequiredArgsConstructor;
import org.afritechinnovations.model.common.*;
import org.afritechinnovations.repository.people.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.Objects;

/** Identifiers are school claims, consumed only after administrator approval and email proof. */
@Service
@RequiredArgsConstructor
@Transactional
public class SchoolIdentityAdmissionService {
    private final StudentRepository students;
    private final TeacherRepository teachers;
    private final org.afritechinnovations.repository.common.SchoolUserRepository memberships;

    public static String normalize(RoleName role, String identifier) {
        if (role != RoleName.STUDENT && role != RoleName.TEACHER) return null;
        if (identifier == null || identifier.isBlank() || identifier.trim().length() > 50
                || identifier.chars().anyMatch(Character::isISOControl)) {
            throw new IllegalArgumentException(role == RoleName.STUDENT
                    ? "Renseignez votre matricule dans cet établissement"
                    : "Renseignez votre numéro d’employé dans cet établissement");
        }
        return identifier.trim();
    }

    public void attachApproved(User applicant, School school, RoleName role, String identifier) {
        String number = normalize(role, identifier);
        if (role == RoleName.STUDENT) {
            var dossier = students.findBySchoolIdAndRegistrationNumberForUpdate(school.getId(), number)
                    .orElseThrow(() -> new IllegalArgumentException("Matricule introuvable dans cet établissement. Vérifiez ou créez le dossier avant l’approbation."));
            requireAvailable(applicant, dossier.getUser());
            if (students.findAllByUserId(applicant.getId()).stream().anyMatch(existing ->
                    school.getId().equals(existing.getSchool().getId()) && !Objects.equals(existing.getId(), dossier.getId()))) {
                throw new IllegalArgumentException("Ce compte possède déjà un autre dossier élève dans cet établissement");
            }
            if (!Objects.equals(applicant.getId(), dossier.getUser().getId())) {
                removePlaceholderAccess(dossier.getUser(), school, role);
                dossier.setUser(applicant);
                students.save(dossier);
            }
        } else if (role == RoleName.TEACHER) {
            var dossier = teachers.findBySchoolIdAndEmployeeNumberForUpdate(school.getId(), number)
                    .orElseThrow(() -> new IllegalArgumentException("Numéro d’employé introuvable dans cet établissement. Vérifiez ou créez le dossier avant l’approbation."));
            requireAvailable(applicant, dossier.getUser());
            if (teachers.findByUserId(applicant.getId()).stream().anyMatch(existing ->
                    school.getId().equals(existing.getSchool().getId()) && !Objects.equals(existing.getId(), dossier.getId()))) {
                throw new IllegalArgumentException("Ce compte possède déjà un autre dossier enseignant dans cet établissement");
            }
            if (!Objects.equals(applicant.getId(), dossier.getUser().getId())) {
                removePlaceholderAccess(dossier.getUser(), school, role);
                dossier.setUser(applicant);
                teachers.save(dossier);
            }
        }
    }

    private void removePlaceholderAccess(User placeholder, School school, RoleName role) {
        memberships.findByUserId(placeholder.getId()).stream()
                .filter(link -> school.getId().equals(link.getSchool().getId()) && role.name().equals(link.getRole().getName()))
                .forEach(memberships::delete);
    }

    /** Never take over a chosen password or a dossier invited at another email address. */
    private void requireAvailable(User applicant, User holder) {
        if (Objects.equals(applicant.getId(), holder.getId())) return;
        if (Boolean.TRUE.equals(holder.getPasswordSet()) || Boolean.TRUE.equals(holder.getEmailVerified())
                || (holder.getEmail() != null && !holder.getEmail().isBlank()) || !Boolean.TRUE.equals(holder.getActive())) {
            throw new IllegalArgumentException("Ce dossier est déjà associé à un autre compte. Vérifiez l’identité et utilisez son invitation.");
        }
    }

    @Transactional(readOnly = true)
    public String review(Long schoolId, RoleName role, String identifier) {
        if (identifier == null || identifier.isBlank()) return "Identifiant à compléter par le demandeur";
        if (role == RoleName.STUDENT) return students.findBySchoolIdAndRegistrationNumber(schoolId, identifier)
                .map(dossier -> dossier.getUser().getFirstName() + " " + dossier.getUser().getLastName())
                .orElse("Matricule introuvable dans cet établissement");
        if (role == RoleName.TEACHER) return teachers.findBySchoolIdAndEmployeeNumber(schoolId, identifier)
                .map(dossier -> dossier.getUser().getFirstName() + " " + dossier.getUser().getLastName())
                .orElse("Numéro d’employé introuvable dans cet établissement");
        return null;
    }
}
