package org.afritechinnovations.dto.self;

import jakarta.validation.constraints.*;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.math.BigDecimal;

public final class ParentPortalDto {
    private ParentPortalDto() {}
    public enum Kind { ANNOUNCEMENT, HOMEWORK, DOCUMENT }
    public enum Decision { ACCEPTED, REJECTED }
    public record PostRequest(@NotNull Kind kind, Long classId, Long studentId,
                              @NotBlank @Size(max=160) String title,
                              @NotBlank @Size(max=4000) String content, LocalDate dueDate) {}
    public record FileItem(Long id, String filename, long sizeBytes) {}
    public record Post(Long id, Kind kind, Long classId, String className, Long studentId, String title, String content,
                       LocalDate dueDate, LocalDateTime createdAt, List<FileItem> files, boolean mine) {}
    public record AppointmentRequest(Long teacherUserId, @NotNull @Future LocalDateTime proposedAt,
                                     @NotBlank @Size(max=1000) String reason) {}
    public record AppointmentDecision(@NotNull Decision status, @NotBlank @Size(max=1000) String response) {}
    public record Appointment(Long id, Long studentId, String studentName, String parentName,
                              String teacherName, LocalDateTime proposedAt, String reason,
                              String status, String response) {}
    public record PaymentItem(Long id, String feeName, BigDecimal amount, LocalDate paymentDate,
                              String method, String reference) {}
    public record EvaluationItem(Long id, String title, String subjectName, String type, LocalDate date) {}
    public record Download(String filename, byte[] data) {}
}
