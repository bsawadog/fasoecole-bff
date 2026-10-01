package org.afritechinnovations.dto.people;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;
import org.afritechinnovations.model.finance.PaymentMethod;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/** Inscription d'un nouvel élève depuis la page « Inscriptions » : identité, classe d'accueil et frais. */
@Getter
@Setter
public class NewStudentEnrollmentRequest extends CreateRosterStudentRequest {

    @NotNull
    private Long classId;

    @Valid
    private List<OwnerEnrollmentDto.FeeLine> fees = new ArrayList<>();

    /** Au moins un parent ou tuteur (nom et prénom obligatoires, courriel et téléphone facultatifs). */
    @Valid
    @NotEmpty(message = "Renseignez au moins un parent ou tuteur")
    @Size(max = 2)
    private List<OwnerEnrollmentDto.Guardian> guardians = new ArrayList<>();

    /** Obligatoire dès qu'un montant est encaissé. */
    private PaymentMethod paymentMethod;

    private LocalDate paymentDate;
}
