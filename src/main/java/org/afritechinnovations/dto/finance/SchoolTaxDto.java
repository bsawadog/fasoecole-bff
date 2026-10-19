package org.afritechinnovations.dto.finance;

import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public final class SchoolTaxDto {
    private SchoolTaxDto() { }
    public enum Code { TVA,IUTS,TPA,PATENTE,INCOME,PROPERTY,OTHER }
    public record Row(Long id,Code code,LocalDate periodStart,LocalDate periodEnd,String origin,
            String declarationReference,long version,boolean carryover,PayableDto.Row obligation) { }
    public record SupplierVat(String key,LocalDate date,boolean estimatedDate,String label,String supplier,String invoiceNumber,String supplierIfu,BigDecimal amount) { }
    public record Overview(int year,List<Row> rows,BigDecimal due,BigDecimal paid,BigDecimal remaining,BigDecimal overdue,List<SupplierVat> supplierVat) {
        public Overview(int year,List<Row> rows,BigDecimal due,BigDecimal paid,BigDecimal remaining,BigDecimal overdue){this(year,rows,due,paid,remaining,overdue,List.of());}
    }
    public record Detail(Row tax,List<PayableDto.Payment> payments,List<Event> events) { }
    public record Event(Long id,String action,BigDecimal amount,LocalDate dueDate,String reason,String author,LocalDateTime createdAt) { }
    public record Create(@NotNull UUID requestId,@NotNull Code code,@NotBlank @Size(max=120) String label,
            @NotNull LocalDate periodStart,@NotNull LocalDate periodEnd,
            @NotNull @DecimalMin("0") @Digits(integer=10,fraction=2) BigDecimal amount,@NotNull LocalDate dueDate,
            @NotBlank @Size(max=150) String creditor,@Size(max=100) String declarationReference,@Size(max=500) String notes) { }
    public record Edit(@PositiveOrZero long version,@NotNull @DecimalMin("0") @Digits(integer=10,fraction=2) BigDecimal amount,
            @NotNull LocalDate dueDate,@NotBlank @Size(max=150) String creditor,
            @Size(max=100) String declarationReference,@Size(max=500) String notes,@NotBlank @Size(max=300) String reason) { }
    public record Cancel(@PositiveOrZero long version,@NotBlank @Size(max=300) String reason) { }
    public record PayrollPreview(String month,BigDecimal iuts,BigDecimal tpa,int remunerations,BigDecimal vacationWithholding) { }
    public record ImportPayroll(@NotBlank String month,@NotNull LocalDate dueDate,@NotBlank @Size(max=150) String creditor,
            @Size(max=100) String declarationReference,@NotBlank @Size(max=300) String reason) { }
}
