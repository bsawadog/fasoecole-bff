package org.afritechinnovations.dto.people;
import lombok.Getter;
import lombok.Setter;
import jakarta.validation.constraints.NotNull;
@Getter @Setter
public class CreateClassTeacherRequest extends CreateTeacherRequest {
    @NotNull(message = "La matière est obligatoire") private Long subjectId;
}
