package org.afritechinnovations.dto.finance;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.afritechinnovations.model.finance.PaymentMethod;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/** Contrats de l'espace « Dépenses & budget » du propriétaire. */
public final class OwnerExpenseDto {

    private OwnerExpenseDto() {
    }

    public record CategoryInfo(Long id, String name, String description, String systemCode, boolean active,
                               long expenseCount) {
    }

    public record CategoryRequest(
            @NotBlank(message = "Le nom de la catégorie est obligatoire") @Size(max = 100) String name,
            @Size(max = 255) String description,
            Boolean active) {
    }

    /** Ligne de dépense : saisie manuelle (MANUAL) ou paiement de la paie des enseignants (PAYROLL, lecture seule). */
    public record ExpenseRow(Long id, String source, LocalDate expenseDate, Long categoryId, String categoryName,
                             String label, String supplier, BigDecimal amount, String method, String reference,
                             String notes, String createdByName) {
    }

    public record ExpenseRequest(
            @NotNull(message = "La catégorie est obligatoire") Long categoryId,
            @NotNull(message = "La date est obligatoire") LocalDate expenseDate,
            @NotNull(message = "Le montant est obligatoire")
            @DecimalMin(value = "0.01", message = "Le montant doit être supérieur à zéro") BigDecimal amount,
            @NotBlank(message = "Le libellé est obligatoire") @Size(max = 150) String label,
            @Size(max = 150) String supplier,
            @NotNull(message = "Le mode de paiement est obligatoire") PaymentMethod method,
            @Size(max = 100) String reference,
            @Size(max = 500) String notes) {
    }

    public record YearInfo(Long id, String label, LocalDate startDate, LocalDate endDate, boolean current) {
    }

    public record MonthLine(String month, BigDecimal income, BigDecimal expenses, BigDecimal balance) {
    }

    public record CategoryLine(Long categoryId, String name, String systemCode, boolean active, BigDecimal budget,
                               BigDecimal spent, BigDecimal remaining, Double usedRate, boolean overBudget) {
    }

    public record Summary(String schoolName, YearInfo year, List<YearInfo> years, LocalDate from, LocalDate to,
                          BigDecimal income, BigDecimal expenses, BigDecimal balance, BigDecimal spentThisMonth,
                          BigDecimal budgetTotal, Double budgetUsedRate, long overBudgetCount,
                          List<MonthLine> months, List<CategoryLine> categories, List<ExpenseRow> recentExpenses) {
    }

    public record BudgetLine(
            @NotNull(message = "La catégorie est obligatoire") Long categoryId,
            @NotNull(message = "Le montant est obligatoire")
            @DecimalMin(value = "0.00", message = "Le budget ne peut pas être négatif") BigDecimal amount) {
    }

    public record BudgetRequest(
            @NotNull(message = "L'année scolaire est obligatoire") Long academicYearId,
            @NotNull(message = "Les lignes du budget sont obligatoires") List<@Valid BudgetLine> lines) {
    }
}
