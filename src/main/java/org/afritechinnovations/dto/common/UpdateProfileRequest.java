package org.afritechinnovations.dto.common;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class UpdateProfileRequest {
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

    @Size(max = 30)
    private String phone;
}
