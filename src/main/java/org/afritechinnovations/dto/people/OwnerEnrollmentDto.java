package org.afritechinnovations.dto.people;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.afritechinnovations.model.people.EnrollmentDecision;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/** Inscriptions, réinscriptions et passage d'année (espace propriétaire). */
public final class OwnerEnrollmentDto {

    private OwnerEnrollmentDto() {
    }

    public record YearInfo(Long id, String label, LocalDate startDate, LocalDate endDate, boolean current,
                           int classCount, long activeStudents, long completedStudents, long pendingDecisions) {
    }

    public record Overview(List<YearInfo> years) {
    }

    public record NewYearRequest(
            @NotBlank @Size(max = 20) String label,
            @NotNull LocalDate startDate,
            @NotNull LocalDate endDate,
            Long sourceYearId,
            boolean copyClasses,
            boolean copyTeachers,
            boolean copyPeriods,
            boolean makeCurrent) {
    }

    public record NewYearResult(YearInfo year, int classesCopied, int assignmentsCopied, int periodsCopied) {
    }

    public record TargetClass(Long id, String name, Long levelId, String levelName, Integer capacity,
                              long enrolled) {
    }

    public record StudentPlan(Long enrollmentId, Long studentId, String firstName, String lastName,
                              String registrationNumber, BigDecimal annualAverage, BigDecimal passMark,
                              EnrollmentDecision suggestedDecision, Long suggestedClassId,
                              boolean decided, EnrollmentDecision decision, Long targetClassId,
                              String targetClassName) {
    }

    public record ClassPlan(Long classId, String className, Long levelId, String levelName,
                            boolean lastLevel, List<StudentPlan> students) {
    }

    public record PromotionPlan(Long fromYearId, String fromYearLabel, Long toYearId, String toYearLabel,
                                List<ClassPlan> classes, List<TargetClass> targetClasses,
                                long pending, long decided) {
    }

    public record DecisionItem(@NotNull Long enrollmentId, @NotNull EnrollmentDecision decision,
                               Long targetClassId) {
    }

    public record PromotionRequest(@NotNull Long fromYearId, @NotNull Long toYearId,
                                   @NotEmpty List<@Valid DecisionItem> decisions) {
    }

    public record Skipped(Long enrollmentId, String studentName, String reason) {
    }

    public record PromotionResult(int applied, List<Skipped> skipped) {
    }

    /** Frais actifs proposés lors de l'inscription d'un nouvel élève (levelId null = tous niveaux). */
    public record EnrollmentFee(Long id, String name, BigDecimal amount, String frequency, Long levelId) {
    }

    /** Frais facturé à l'inscription ; amountPaid > 0 encaisse immédiatement un paiement. */
    public record FeeLine(@NotNull Long feeTypeId, @DecimalMin("0") BigDecimal amountPaid) {
    }

    /**
     * Parent ou tuteur saisi à l'inscription : seuls le nom et le prénom sont obligatoires.
     * Avec parentId, un parent déjà enregistré est rattaché tel quel (ses coordonnées ne sont pas modifiées).
     */
    public record Guardian(Long parentId,
                           @NotBlank @Size(max = 100) String firstName,
                           @NotBlank @Size(max = 100) String lastName,
                           @Email @Size(max = 150) String email,
                           @Size(max = 30) String phone,
                           @Size(max = 50) String relationship) {
    }

    /** Parent existant proposé à l'inscription, avec ses enfants déjà inscrits pour le reconnaître. */
    public record GuardianOption(Long parentId, String firstName, String lastName, String email, String phone,
                                 List<String> children) {
    }

    public record RegistrationResult(ClassRosterRowDto student, String schoolName, String className,
                                     String yearLabel, List<StudentDetailDto.InvoiceInfo> invoices) {
    }
}
