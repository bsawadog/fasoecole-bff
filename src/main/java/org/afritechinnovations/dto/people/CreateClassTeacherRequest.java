package org.afritechinnovations.dto.people;
import lombok.Getter;
import lombok.Setter;
import jakarta.validation.constraints.NotNull;
@Getter @Setter
public class CreateClassTeacherRequest extends CreateTeacherRequest {
    private Long subjectId;

    @com.fasterxml.jackson.annotation.JsonIgnore
    @jakarta.validation.constraints.AssertTrue(message = "Choisissez au moins une matière")
    public boolean isSubjectSelectionPresent() {
        return subjectId != null || (getSubjectIds() != null && !getSubjectIds().isEmpty());
    }
}
