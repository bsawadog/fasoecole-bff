package org.afritechinnovations.dto.people;

import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;
import org.afritechinnovations.model.academic.AttendanceStatus;

import java.time.LocalDate;

@Getter
@Setter
public class UpsertAttendanceRequest {

    @NotNull(message = "La date est obligatoire")
    private LocalDate attendanceDate;

    @NotNull(message = "Le statut est obligatoire")
    private AttendanceStatus status;

    private String justification;
}
