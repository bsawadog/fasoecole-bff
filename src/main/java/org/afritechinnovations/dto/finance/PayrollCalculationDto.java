package org.afritechinnovations.dto.finance;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class PayrollCalculationDto {
    private PayrollCalculationDto() { }
    public enum Regime { SALARIED, TEACHING_VACATION }
    public record Input(@NotNull Regime regime,
            @NotNull @DecimalMin("0") @Digits(integer=10,fraction=2) BigDecimal baseSalary,
            @NotNull @DecimalMin("0") @Digits(integer=10,fraction=2) BigDecimal housing,
            @NotNull @DecimalMin("0") @Digits(integer=10,fraction=2) BigDecimal functionAllowance,
            @NotNull @DecimalMin("0") @Digits(integer=10,fraction=2) BigDecimal transport,
            @NotNull @DecimalMin("0") @Digits(integer=10,fraction=2) BigDecimal benefitsInKind,
            @NotNull @DecimalMin("0") @Digits(integer=10,fraction=2) BigDecimal pension,
            @NotNull @DecimalMin("0") @Digits(integer=10,fraction=2) BigDecimal otherSocial,
            @NotNull @DecimalMin("0") @Digits(integer=10,fraction=2) BigDecimal otherDeductions,
            @NotNull @DecimalMin("0") @Digits(integer=10,fraction=2) BigDecimal employerContributions,
            boolean upperCategory, @Min(0) @Max(4) int familyCharges,
            @NotNull Boolean tpaExempt, boolean approvedManagementCenter,
            @NotBlank @Size(max=300) String reason) { }
    public record Bracket(BigDecimal from, BigDecimal to, BigDecimal rate, BigDecimal amount) { }
    public record Result(String ruleVersion, String month, Input input, BigDecimal gross,
            BigDecimal pensionDeductible, BigDecimal professionalAllowance, BigDecimal exemptions,
            BigDecimal taxableBase, List<Bracket> brackets, BigDecimal grossIuts, BigDecimal familyReduction,
            BigDecimal iuts, BigDecimal vacationWithholding, BigDecimal deductions, BigDecimal net,
            BigDecimal tpaBase, BigDecimal tpa, BigDecimal employerCost) { }
    public record Preview(Result result, Map<Long,Long> versions, Map<Long,BigDecimal> grossByPayable) { }
    public record Apply(@NotNull @Valid Input input, @NotNull UUID requestId,
            @NotNull Map<Long,Long> versions, @NotNull Map<Long,BigDecimal> grossByPayable) { }
}
