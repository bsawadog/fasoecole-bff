package org.afritechinnovations.dto.auth;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class VerifyEmailRequest {
    @NotBlank
    private String token;

    /** Obligatoire pour l'activation d'un compte créé par une école. */
    @Size(min = 8, max = 100)
    private String newPassword;
}
