package org.afritechinnovations.dto.people;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;

@Getter
@Setter
public class CreateRosterStudentRequest {

    @NotBlank
    @Size(max = 100)
    private String firstName;

    @NotBlank
    @Size(max = 100)
    private String lastName;

    @NotBlank
    @Email
    @Size(max = 150)
    private String email;

    /** Ancien champ conservé pour compatibilité ; le mot de passe est choisi par invitation. */
    @Size(max = 100)
    private String password;

    @Size(max = 30)
    private String phone;

    /** Facultatif : généré automatiquement (MAT-AAAA-NNN) s'il est laissé vide. */
    @Size(max = 50)
    private String registrationNumber;

    private LocalDate birthDate;

    @Size(max = 10)
    private String gender;
}
