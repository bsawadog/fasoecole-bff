package org.afritechinnovations.dto.finance;

import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public final class SchoolResourcesDto {
    private SchoolResourcesDto() { }
    public enum AssetCategory { LAND, BUILDING, ROOM, DESK_BENCH, TABLE, CHAIR, CABINET, COMPUTER, EQUIPMENT, OTHER }
    public enum Ownership { OWNED, RENTED, BORROWED }
    public enum AssetCondition { GOOD, FAIR, DAMAGED, UNUSABLE }
    public enum AssetStatus { IN_USE, STORED, MAINTENANCE, RETIRED }
    public enum StockCategory { CLEANING, TEACHING, OFFICE, SMALL_EQUIPMENT, OTHER }
    public enum MovementKind { IN, OUT, COUNT }
    public record AssetInput(
            @NotBlank @Size(max=40) String code, @NotBlank @Size(max=150) String name,
            @NotNull AssetCategory category, @NotBlank @Size(max=150) String location,
            @Size(max=150) String custodian, @NotNull Ownership ownership, @NotNull AssetCondition condition,
            @NotNull AssetStatus status, @NotNull @Min(1) @Max(1000000) Integer quantity,
            @DecimalMin("0") @Digits(integer=12,fraction=2) BigDecimal unitValue,
            @PastOrPresent LocalDate acquiredOn, LocalDate maintenanceOn, @Size(max=2000) String notes,
            @Min(0) Long version, @Size(max=500) String changeReason) { }
    public record Asset(Long id, String code, String name, AssetCategory category, String location,
            String custodian, Ownership ownership, AssetCondition condition, AssetStatus status, int quantity,
            BigDecimal unitValue, LocalDate acquiredOn, LocalDate maintenanceOn, String notes, long version) { }
    public record AssetEvent(Long id, String type, String reason, String snapshot, String actor, LocalDateTime createdAt) { }
    public record AssetsOverview(List<Asset> assets) { }
    public record StockInput(
            @NotBlank @Size(max=40) String code, @NotBlank @Size(max=150) String name,
            @NotNull StockCategory category, @NotBlank @Size(max=30) String unit,
            @NotBlank @Size(max=150) String location,
            @NotNull @DecimalMin("0") @Digits(integer=11,fraction=3) BigDecimal minimumQuantity,
            @DecimalMin("0") @Digits(integer=12,fraction=2) BigDecimal unitCost,
            boolean active, @Size(max=2000) String notes, @Min(0) Long version) { }
    public record StockItem(Long id, String code, String name, StockCategory category, String unit,
            String location, BigDecimal minimumQuantity, BigDecimal quantity, BigDecimal unitCost,
            boolean active, String notes, long version) { }
    public record StockOverview(List<StockItem> items) { }
    public record MovementInput(
            @NotNull UUID requestId, @NotNull MovementKind kind, @NotNull @PastOrPresent LocalDate date,
            @NotNull @DecimalMin("0") @Digits(integer=11,fraction=3) BigDecimal quantity,
            @NotBlank @Size(max=500) String reason, @Size(max=150) String recipient,
            @Size(max=100) String reference) { }
    public record StockMovement(Long id, MovementKind kind, LocalDate date, BigDecimal requestedQuantity,
            BigDecimal quantityChange, BigDecimal balanceAfter, String reason, String recipient,
            String reference, String actor, LocalDateTime createdAt) { }
}
