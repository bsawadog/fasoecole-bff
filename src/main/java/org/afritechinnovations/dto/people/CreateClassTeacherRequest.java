package org.afritechinnovations.dto.people;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;

@Getter
@Setter
public class CreateClassTeacherRequest {

    @NotBlank(message = "Le prénom est obligatoire")
    @Size(max = 100)
    private String firstName;

    @NotBlank(message = "Le nom est obligatoire")
    @Size(max = 100)
    private String lastName;

    @NotBlank(message = "Le courriel est obligatoire")
    @Email(message = "Courriel invalide")
    @Size(max = 150)
    private String email;

    @NotBlank(message = "Le mot de passe est obligatoire")
    @Size(min = 8, max = 100, message = "Le mot de passe doit contenir au moins 8 caractères")
    private String password;

    @Size(max = 30)
    private String phone;

    @Size(max = 150)
    private String specialty;

    private LocalDate hireDate;

    @NotNull(message = "La matière est obligatoire")
    private Long subjectId;
}
