package org.afritechinnovations.dto.people;

import lombok.*;
import org.afritechinnovations.model.people.EnrollmentStatus;

import java.time.LocalDate;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class StudentEnrollmentDto {
    private Long id;
    private Long studentId;
    private Long classId;
    private Long academicYearId;
    private EnrollmentStatus status;
    private LocalDate enrollmentDate;
}
