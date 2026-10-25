package org.afritechinnovations.dto.people;

import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

/** Initial timetable includes the subject assignment needed by payroll and the teacher space. */
@Getter
@Setter
public class InitialTeacherSlotRequest extends TeacherSlotRequest {
    @NotNull(message = "La matière est obligatoire pour chaque créneau")
    private Long subjectId;
}
