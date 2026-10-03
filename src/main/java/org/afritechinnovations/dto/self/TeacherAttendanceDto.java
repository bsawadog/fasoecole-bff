package org.afritechinnovations.dto.self;

import jakarta.validation.constraints.*;
import org.afritechinnovations.model.academic.AttendanceStatus;
import java.time.LocalDate;

public final class TeacherAttendanceDto {
    private TeacherAttendanceDto() {}
    public record Item(Long studentId, String fullName, String registrationNumber, AttendanceStatus status, String justification) {}
    public record Request(@NotNull @PastOrPresent LocalDate date, @NotNull AttendanceStatus status,
                          @Size(max=500) String justification) {}
}
