package org.afritechinnovations.dto.common;

import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;
import org.afritechinnovations.model.common.RoleName;

@Getter
@Setter
public class CreateSchoolAccessRequest {
    @NotNull
    private Long schoolId;

    @NotNull
    private RoleName requestedRole;
}
