package org.afritechinnovations.dto.finance;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/** Taxes invoiced by the supplier and already included in the gross payable. */
public record ExpenseTaxDetails(@Size(max=100) String invoiceNumber, LocalDate invoiceDate,
        @Size(max=50) String supplierIfu, @NotNull @DecimalMin("0") BigDecimal excludingTax,
        @NotNull @Size(max=20) List<@Valid Line> taxes) {
    public record Line(@NotBlank @Size(max=100) String label,
                       @NotNull @DecimalMin("0.01") @Digits(integer=10,fraction=2) BigDecimal amount) {}
}
