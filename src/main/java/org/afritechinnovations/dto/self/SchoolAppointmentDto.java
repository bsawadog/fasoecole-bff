package org.afritechinnovations.dto.self;

import jakarta.validation.constraints.*;
import java.time.LocalDateTime;

public final class SchoolAppointmentDto {
    private SchoolAppointmentDto() {}
    public record Request(@NotNull Long recipientUserId, @NotNull @Future LocalDateTime proposedAt,
                          @NotBlank @Size(max=1000) String reason) {}
    public record Appointment(Long id, Long schoolId, String schoolName, String organizerName,
                              String recipientName, LocalDateTime proposedAt, String reason,
                              String status, String response, boolean mine) {}
}
