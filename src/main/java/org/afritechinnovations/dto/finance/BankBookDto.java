package org.afritechinnovations.dto.finance;

import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public final class BankBookDto {
    private BankBookDto() {}
    public enum Direction { IN, OUT }
    public record Open(@NotNull LocalDate date, @NotNull @Digits(integer=12,fraction=2) BigDecimal openingBalance) {}
    public record Entry(@NotNull UUID requestId, @NotNull LocalDate date, @NotNull Direction direction,
                        @NotNull @DecimalMin("0.01") @Digits(integer=10,fraction=2) BigDecimal amount,
                        @NotBlank @Size(max=150) String label, @Size(max=100) String reference) {}
    public record Book(LocalDate openedOn, BigDecimal openingBalance) {}
    public record Movement(Long id, LocalDate date, Direction direction, BigDecimal amount,
                           String label, String reference, String source, BigDecimal balance) {}
    public record Overview(Book book, String month, BigDecimal openingBalance, BigDecimal receipts,
                           BigDecimal payments, BigDecimal closingBalance, List<Movement> rows) {}
}
