package org.afritechinnovations.dto.finance;

import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public final class CashBookDto {
    private CashBookDto() {}
    public enum Direction { IN, OUT }
    public record Open(@NotNull LocalDate date,
                       @NotNull @DecimalMin("0") @Digits(integer=12,fraction=2) BigDecimal openingBalance) {}
    public record Entry(@NotNull UUID requestId, @NotNull LocalDate date,
                        @NotNull Direction direction,
                        @NotNull @DecimalMin("0.01") @Digits(integer=10,fraction=2) BigDecimal amount,
                        @NotBlank @Size(max=150) String label, @Size(max=100) String reference) {}
    public record Close(@NotBlank @Pattern(regexp="^\\d{4}-(0[1-9]|1[0-2])$") String month,
                        @NotNull @DecimalMin("0") @Digits(integer=12,fraction=2) BigDecimal countedBalance,
                        @Size(max=500) String note, boolean finalClosure) {}
    public record Book(LocalDate openedOn, BigDecimal openingBalance, LocalDate closedOn) {}
    public record Movement(Long id, LocalDate date, Direction direction, BigDecimal amount,
                           String label, String reference, String source, BigDecimal balance) {}
    public record Closure(String month, BigDecimal openingBalance, BigDecimal receipts,
                          BigDecimal payments, BigDecimal closingBalance, BigDecimal countedBalance,
                          String note, LocalDateTime closedAt) {}
    public record Overview(Book book, String month, BigDecimal openingBalance, BigDecimal receipts,
                           BigDecimal payments, BigDecimal closingBalance, Closure closure,
                           List<Movement> rows, List<Closure> history) {}
}
