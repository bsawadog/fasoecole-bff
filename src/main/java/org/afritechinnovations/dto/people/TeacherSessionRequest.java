package org.afritechinnovations.dto.people;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;

@Getter
@Setter
public class TeacherSessionRequest {

    @NotNull(message = "Le créneau est obligatoire")
    private Long slotId;

    @NotNull(message = "La date est obligatoire")
    private LocalDate date;

    /** PENDING efface un pointage existant. */
    @NotBlank(message = "Le statut est obligatoire")
    @Pattern(regexp = "PRESENT|ABSENT|PENDING", message = "Le statut doit être PRESENT, ABSENT ou PENDING")
    private String status;
}