package org.afritechinnovations.dto.people;

import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class AssignClassTeacherRequest {

    @NotNull(message = "L'enseignant est obligatoire")
    private Long teacherId;

    @NotNull(message = "La matière est obligatoire")
    private Long subjectId;
}
