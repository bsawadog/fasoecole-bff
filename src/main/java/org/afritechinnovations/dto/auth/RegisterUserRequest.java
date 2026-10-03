package org.afritechinnovations.dto.auth;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;
import org.afritechinnovations.model.common.RoleName;

@Getter
@Setter
public class RegisterUserRequest {
    @jakarta.validation.constraints.Size(max = 50)
    private String schoolIdentifier;

    @jakarta.validation.constraints.Size(max = 20)
    private java.util.List<@jakarta.validation.constraints.NotBlank @jakarta.validation.constraints.Size(max = 50) String> childRegistrationNumbers;

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

    @NotBlank
    @Size(min = 8, max = 100)
    private String password;

    @Size(max = 30)
    private String phone;

    @NotNull
    private Long schoolId;

    @NotNull
    private RoleName requestedRole;
}
