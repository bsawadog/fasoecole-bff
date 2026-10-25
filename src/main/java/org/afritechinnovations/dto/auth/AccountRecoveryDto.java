package org.afritechinnovations.dto.auth;

import jakarta.validation.constraints.*;
import java.time.LocalDateTime;
import io.swagger.v3.oas.annotations.media.Schema;

public final class AccountRecoveryDto {
    private AccountRecoveryDto() { }
    @Schema(name="AccountRecoverySubmit")
    public record Submit(@NotNull Long schoolId,
        @NotBlank @Size(max=50) String registrationNumber,
        @NotBlank @Size(max=100) String firstName, @NotBlank @Size(max=100) String lastName,
        @NotBlank @Email @Size(max=150) String newEmail, @NotBlank @Size(min=10,max=1000) String reason) { }
    @Schema(name="AccountRecoveryDecision")
    public record Decision(boolean approve, boolean identityVerified,
        @NotBlank @Size(min=10,max=1000) String verificationNote) { }
    @Schema(name="AccountRecoveryComplete")
    public record Complete(@NotBlank @Size(max=100) String token,
        @NotBlank @Size(min=8,max=100) String newPassword) { }
    @Schema(name="AccountRecoveryView")
    public record View(Long id, Long schoolId, String schoolName, Long studentId, String firstName, String lastName,
        String registrationNumber, String newEmail, String reason, String status,
        LocalDateTime createdAt, LocalDateTime decidedAt, LocalDateTime completedAt,
        Long decidedBy, String verificationNote, String deliveryStatus, String oldNotificationStatus,
        LocalDateTime expiresAt, int sendCount, java.util.List<String> affectedSchools, java.util.List<Audit> history) { }
    @Schema(name="AccountRecoveryAudit")
    public record Audit(Long actorId, String action, String details, LocalDateTime createdAt) { }
}
