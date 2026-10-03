package org.afritechinnovations.dto.people;

import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class CreateRosterParentRequest extends UpdateParentProfileRequest {
    @Size(max = 50)
    private String relationship;
}
