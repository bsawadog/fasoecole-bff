package org.afritechinnovations.dto.people;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;

@Getter
@Setter
public class TeacherSlotRequest {

    public static final String TIME_PATTERN = "^([01]\\d|2[0-3]):[0-5]\\d$";

    @NotNull(message = "La classe est obligatoire")
    private Long classId;

    @NotNull(message = "Le jour est obligatoire")
    @Min(value = 1, message = "Le jour doit être compris entre 1 (lundi) et 7 (dimanche)")
    @Max(value = 7, message = "Le jour doit être compris entre 1 (lundi) et 7 (dimanche)")
    private Integer dayOfWeek;

    @NotBlank(message = "L'heure de début est obligatoire")
    @Pattern(regexp = TIME_PATTERN, message = "Heure de début invalide (HH:mm)")
    private String startTime;

    @NotBlank(message = "L'heure de fin est obligatoire")
    @Pattern(regexp = TIME_PATTERN, message = "Heure de fin invalide (HH:mm)")
    private String endTime;

    @NotNull(message = "La date d'effet est obligatoire")
    private LocalDate effectiveFrom;
}