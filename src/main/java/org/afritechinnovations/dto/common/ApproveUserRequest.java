package org.afritechinnovations.dto.common;

import jakarta.validation.constraints.NotNull;
import org.afritechinnovations.model.common.RoleName;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ApproveUserRequest {
    @NotNull
    private Long schoolId;

    @NotNull
    private RoleName role;

    private Long classId;

    @jakarta.validation.constraints.Size(max = 50)
    private String registrationNumber;
}
