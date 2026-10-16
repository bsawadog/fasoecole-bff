package org.afritechinnovations.dto.people;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class UpdateParentProfileRequest {

    /** Élève concerné lorsque le parent est remplacé par un compte existant. */
    @jakarta.validation.constraints.Positive
    private Long studentId;

    @NotBlank
    @Size(max = 100)
    private String firstName;

    @NotBlank
    @Size(max = 100)
    private String lastName;

    @Email
    @Size(max = 150)
    private String email;

    @Size(max = 30)
    private String phone;
}
