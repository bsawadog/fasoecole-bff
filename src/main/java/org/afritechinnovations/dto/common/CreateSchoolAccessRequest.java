package org.afritechinnovations.dto.common;

import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;
import org.afritechinnovations.model.common.RoleName;

@Getter
@Setter
public class CreateSchoolAccessRequest {
    @jakarta.validation.constraints.Size(max = 50)
    private String schoolIdentifier;

    @jakarta.validation.constraints.Size(max = 20)
    private java.util.List<@jakarta.validation.constraints.NotBlank @jakarta.validation.constraints.Size(max = 50) String> childRegistrationNumbers;

    @NotNull
    private Long schoolId;

    @NotNull
    private RoleName requestedRole;
}
