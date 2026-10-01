package org.afritechinnovations.dto.people;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;

@Getter
@Setter
public class TeacherPaymentRequest {

    @NotBlank(message = "Le mois est obligatoire")
    @Pattern(regexp = "^\\d{4}-(0[1-9]|1[0-2])$", message = "Mois invalide (YYYY-MM)")
    private String month;

    @NotNull(message = "La date de paiement est obligatoire")
    private LocalDate date;

    @NotNull(message = "Le montant est obligatoire")
    @DecimalMin(value = "0.01", message = "Le montant doit être supérieur à zéro")
    @Digits(integer = 10, fraction = 2, message = "Montant invalide (2 décimales maximum)")
    private BigDecimal amount;

    @Size(max = 100, message = "La référence ne peut pas dépasser 100 caractères")
    private String reference;
}