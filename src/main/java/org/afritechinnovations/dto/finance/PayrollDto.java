package org.afritechinnovations.dto.finance;

import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public final class PayrollDto {
    private PayrollDto() { }
    public enum DocumentKind { PAYSLIP, EMPLOYMENT_CERTIFICATE }
    public record SchoolInfo(Long id, String name, String address, String phone, String email) { }
    public record Contract(String key, String kind, String jobTitle, String employeeNumber, boolean active,
            LocalDate hireDate, String rateType, BigDecimal rate, boolean eligible, boolean prepared,
            Long payableId, BigDecimal due, BigDecimal paid, BigDecimal remaining,
            BigDecimal workedHours, BigDecimal extraHours, List<PayableDto.Payment> payments,
            BigDecimal iuts, BigDecimal tpa, BigDecimal net, long taxVersion,
            BigDecimal deductions, PayrollCalculationDto.Result calculation) { }
    public record TaxInput(@NotNull @DecimalMin("0") @Digits(integer=10,fraction=2) BigDecimal iuts,
            @NotNull @DecimalMin("0") @Digits(integer=10,fraction=2) BigDecimal tpa,
            @PositiveOrZero long version, @NotBlank @Size(max=300) String reason) { }
    public record Employee(Long userId, String firstName, String lastName, String email, String phone,
            boolean active, List<String> subjects, List<Contract> contracts, BigDecimal due, BigDecimal paid,
            BigDecimal remaining, String status) { }
    public record Overview(SchoolInfo school, String month, List<Employee> employees,
            BigDecimal due, BigDecimal paid, BigDecimal remaining) { }
    public record MonthHistory(String month, BigDecimal due, BigDecimal paid, BigDecimal remaining, String status, BigDecimal iuts, BigDecimal net, BigDecimal tpa) { }
    public record EmployeeDetail(SchoolInfo school, String month, Employee employee,
            List<MonthHistory> history, List<DocumentInfo> documents) { }
    public record DocumentInput(@NotNull DocumentKind kind, @NotNull Long userId,
            @Pattern(regexp="\\d{4}-(0[1-9]|1[0-2])") String month, @NotNull UUID requestId) { }
    public record Snapshot(SchoolInfo school, String month, Employee employee, LocalDate issuedOn) { }
    public record Document(Long id, String reference, DocumentKind kind, String month,
            LocalDateTime createdAt, Snapshot snapshot) { }
    public record DocumentInfo(Long id, String reference, DocumentKind kind, String month, LocalDateTime createdAt) { }
}
