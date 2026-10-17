package org.afritechinnovations.dto.finance;

import jakarta.validation.constraints.*;
import org.afritechinnovations.model.finance.PaymentMethod;
import java.math.BigDecimal;
import java.time.LocalDate;

public record ImportTeacherSalaryRequest(
        @NotBlank @Pattern(regexp="\\d{4}-(0[1-9]|1[0-2])") String month,
        @NotNull @DecimalMin("0.01") @Digits(integer=10,fraction=2) BigDecimal salary,
        @DecimalMin("0.00") @Digits(integer=10,fraction=2) BigDecimal paid,
        LocalDate paymentDate, PaymentMethod paymentMethod) { }
