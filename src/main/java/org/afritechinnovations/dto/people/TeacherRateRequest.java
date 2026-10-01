package org.afritechinnovations.dto.people;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;

@Getter
@Setter
public class TeacherRateRequest {

    @NotBlank(message = "Le type de taux est obligatoire")
    @Pattern(regexp = "HOURLY|MONTHLY", message = "Le type de taux doit être HOURLY ou MONTHLY")
    private String type;

    @NotNull(message = "Le montant est obligatoire")
    @DecimalMin(value = "0.01", message = "Le montant doit être supérieur à zéro")
    @Digits(integer = 10, fraction = 2, message = "Montant invalide (2 décimales maximum)")
    private BigDecimal amount;

    @NotNull(message = "La date d'effet est obligatoire")
    private LocalDate effectiveFrom;
}