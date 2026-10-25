package org.afritechinnovations.dto.finance;

import jakarta.validation.constraints.*;
import jakarta.validation.Valid;
import org.afritechinnovations.model.finance.PaymentMethod;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public final class PayableDto {
    private PayableDto() {}
    public record Row(Long id, String source, String period, LocalDate dueDate, String label, String supplier,
                      Long categoryId, String categoryName, BigDecimal amount, BigDecimal paid,
                      BigDecimal remaining, String status, boolean overdue, String notes, ExpenseTaxDetails taxDetails) {
        public Row(Long id,String source,String period,LocalDate dueDate,String label,String supplier,Long categoryId,String categoryName,BigDecimal amount,BigDecimal paid,BigDecimal remaining,String status,boolean overdue,String notes){this(id,source,period,dueDate,label,supplier,categoryId,categoryName,amount,paid,remaining,status,overdue,notes,null);}
    }
    public record Payment(Long id, LocalDate date, BigDecimal amount, String method, String reference) {}
    public record FixedCharge(Long id, Long categoryId, String label, String supplier, BigDecimal amount,
                              int dueDay, String startMonth, boolean active, ExpenseTaxDetails taxDetails) {
        public FixedCharge(Long id,Long categoryId,String label,String supplier,BigDecimal amount,int dueDay,String startMonth,boolean active){this(id,categoryId,label,supplier,amount,dueDay,startMonth,active,null);}
    }
    public record Overview(List<Row> rows, List<FixedCharge> fixedCharges, BigDecimal total,
                           BigDecimal paid, BigDecimal remaining, BigDecimal overdue) {}
    public record Create(@NotNull Long categoryId, @NotBlank @Size(max=150) String label,
                         @Size(max=150) String supplier, @NotNull @DecimalMin("0.01")
                         @Digits(integer=10, fraction=2) BigDecimal amount,
                         @NotNull LocalDate dueDate, @Size(max=500) String notes, @Valid ExpenseTaxDetails taxDetails) {
        public Create(Long categoryId,String label,String supplier,BigDecimal amount,LocalDate dueDate,String notes){this(categoryId,label,supplier,amount,dueDate,notes,null);}
    }
    public record FixedRequest(@NotNull Long categoryId, @NotBlank @Size(max=150) String label,
                               @Size(max=150) String supplier, @NotNull @DecimalMin("0.01")
                               @Digits(integer=10, fraction=2) BigDecimal amount,
                               @Min(1) @Max(31) int dueDay, @NotBlank String startMonth,@Valid ExpenseTaxDetails taxDetails) {
        public FixedRequest(Long categoryId,String label,String supplier,BigDecimal amount,int dueDay,String startMonth){this(categoryId,label,supplier,amount,dueDay,startMonth,null);}
    }
    public record Pay(@NotNull UUID requestId, @NotNull @DecimalMin("0.01")
                      @Digits(integer=10, fraction=2) BigDecimal amount, @NotNull LocalDate date,
                      @NotNull PaymentMethod method, @Size(max=100) String reference) {}
}
