package org.afritechinnovations.dto.finance;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.afritechinnovations.model.finance.FeeFrequency;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/** Contrats de l'espace « Frais & paiements » du propriétaire. */
public final class OwnerFinanceDto {

    private OwnerFinanceDto() {
    }

    public record FeeTypeInfo(Long id, String name, BigDecimal amount, FeeFrequency frequency, Long levelId,
                              String levelName, String description, boolean active, long invoiceCount) {
    }

    public record FeeTypeRequest(
            @NotBlank(message = "Le nom du frais est obligatoire") @Size(max = 150) String name,
            @NotNull(message = "Le montant est obligatoire")
            @DecimalMin(value = "0.00", message = "Le montant ne peut pas être négatif") BigDecimal amount,
            FeeFrequency frequency,
            Long levelId,
            @Size(max = 255) String description,
            Boolean active) {
    }

    public record BulkInvoiceRequest(
            @NotNull(message = "Le type de frais est obligatoire") Long feeTypeId,
            Long classId,
            Long levelId,
            @NotNull(message = "La date d'échéance est obligatoire") LocalDate dueDate,
            @DecimalMin(value = "0.01", message = "Le montant doit être supérieur à zéro") BigDecimal amount) {
    }

    public record BulkInvoiceResult(int created, int skipped, int targetedStudents) {
    }

    public record DiscountRequest(
            @NotNull(message = "Le montant de la réduction est obligatoire")
            @DecimalMin(value = "0.00", message = "La réduction ne peut pas être négative") BigDecimal amount,
            @Size(max = 255) String reason) {
    }

    public record InvoiceRow(Long id, Long studentId, String studentName, String registrationNumber, Long classId,
                             String className, Long feeTypeId, String feeTypeName, BigDecimal amountDue,
                             BigDecimal discountAmount, String discountReason, BigDecimal netAmount,
                             BigDecimal paid, BigDecimal balance, LocalDate dueDate, String status, long daysOverdue) {
    }

    public record PaymentRow(Long id, String reference, LocalDate paymentDate, BigDecimal amount, String method,
                             Long invoiceId, Long studentId, String studentName, String registrationNumber,
                             String className, String feeTypeName, BigDecimal invoiceNet, BigDecimal invoiceBalance) {
    }

    public record ClassBreakdown(Long classId, String className, long students, BigDecimal expected,
                                 BigDecimal collected, BigDecimal remaining, long unpaidInvoices) {
    }

    public record FeeBreakdown(Long feeTypeId, String feeTypeName, BigDecimal expected, BigDecimal collected,
                               BigDecimal remaining) {
    }

    public record Overview(String schoolName, BigDecimal expected, BigDecimal discounts, BigDecimal collected,
                           BigDecimal remaining, BigDecimal overdueAmount, BigDecimal collectedThisMonth,
                           double collectionRate, long invoiceCount, long unpaidCount, long overdueCount,
                           long paidCount, List<ClassBreakdown> perClass, List<FeeBreakdown> perFeeType,
                           List<PaymentRow> recentPayments) {
    }

    public record ReminderResult(int recipients) {
    }
}
