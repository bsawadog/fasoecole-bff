package org.afritechinnovations.dto.common;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import org.afritechinnovations.model.common.StaffModule;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

/** Contrats de l'API « Personnel & accès » de l'espace propriétaire. */
public final class OwnerStaffDto {

    private OwnerStaffDto() {
    }

    public record ModuleInfo(String code, String label) {
    }

    public record StaffRow(Long id, Long userId, String firstName, String lastName, String email, String phone,
                           String jobTitle, List<String> modules, boolean active, boolean managedAccount,
                           LocalDateTime createdAt, Boolean emailVerified, String invitationDeliveryStatus) {
    }

    public record StaffRequest(
            @NotBlank(message = "Le prénom est obligatoire") @Size(max = 100) String firstName,
            @NotBlank(message = "Le nom est obligatoire") @Size(max = 100) String lastName,
            @Email(message = "Adresse e-mail invalide") @Size(max = 150) String email,
            @Size(max = 30) String phone,
            @NotBlank(message = "La fonction est obligatoire") @Size(max = 80) String jobTitle,
            @jakarta.validation.constraints.NotNull Set<StaffModule> modules) {
    }

    /** temporaryPassword reste null pour compatibilité ; seul le titulaire choisit son mot de passe. */
    public record StaffCreated(StaffRow staff, String temporaryPassword, boolean existingAccount,
                               boolean emailSent) {
    }

    public record PasswordReset(String temporaryPassword, boolean emailSent) {
    }

    /** Établissement accessible à l'utilisateur courant dans l'espace propriétaire. */
    public record SchoolAccess(Long schoolId, String schoolName, String schoolType, boolean owner,
                               String jobTitle, List<String> modules, String status, LocalDateTime submittedAt) {
        public SchoolAccess(Long schoolId, String schoolName, String schoolType, boolean owner, String jobTitle, List<String> modules) {
            this(schoolId, schoolName, schoolType, owner, jobTitle, modules, "ACTIVE", null);
        }
    }
}
