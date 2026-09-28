package org.afritechinnovations.dto.academic;

import lombok.*;
import org.afritechinnovations.model.academic.AttendanceStatus;

import java.time.LocalDate;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AttendanceDto {
    private Long id;
    private Long studentId;
    private Long classId;
    private LocalDate attendanceDate;
    private AttendanceStatus status;
    private String justification;
}
