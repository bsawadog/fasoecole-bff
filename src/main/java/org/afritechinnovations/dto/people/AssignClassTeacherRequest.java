package org.afritechinnovations.dto.people;

import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class AssignClassTeacherRequest {

    @NotNull(message = "L'enseignant est obligatoire")
    private Long teacherId;

    private Long subjectId;

    @jakarta.validation.constraints.Size(max = 100)
    private java.util.List<@NotNull Long> subjectIds = new java.util.ArrayList<>();

    @com.fasterxml.jackson.annotation.JsonIgnore
    @jakarta.validation.constraints.AssertTrue(message = "Choisissez au moins une matière")
    public boolean isSubjectSelectionPresent() {
        return subjectId != null || (subjectIds != null && !subjectIds.isEmpty());
    }
}
