package org.afritechinnovations.dto.people;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;

@Getter
@Setter
public class TeacherExtraRequest {

    @NotNull(message = "La classe est obligatoire")
    private Long classId;

    @NotNull(message = "La date est obligatoire")
    private LocalDate date;

    @NotNull(message = "Le nombre d'heures est obligatoire")
    @DecimalMin(value = "0.01", message = "Le nombre d'heures doit être supérieur à zéro")
    @DecimalMax(value = "24", message = "Le nombre d'heures ne peut pas dépasser 24")
    @Digits(integer = 2, fraction = 2, message = "Nombre d'heures invalide (2 décimales maximum)")
    private BigDecimal hours;

    @Size(max = 255, message = "La description ne peut pas dépasser 255 caractères")
    private String description;
}