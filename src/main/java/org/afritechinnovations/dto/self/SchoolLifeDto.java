package org.afritechinnovations.dto.self;

import jakarta.validation.constraints.*;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

public final class SchoolLifeDto {
    private SchoolLifeDto() { }
    public enum LifeModule { CALENDAR, DISCIPLINE, REQUESTS, LIBRARY, HOMEWORK }
    public enum EventKind { EXAM, HOLIDAY, MEETING, EVENT }
    public enum ObservationKind { INCIDENT, POSITIVE }
    public enum DocumentKind { SCHOOL_CERTIFICATE, ATTESTATION, OTHER }
    public enum RequestStatus { IN_PROGRESS, READY, REJECTED }
    public enum HomeworkStatus { TO_DO, SUBMITTED, CORRECTED }

    public record EventRequest(Long classId, @NotBlank @Size(max=160) String title,
            @NotNull @Size(max=2000) String description, @NotNull EventKind kind,
            @NotNull LocalDate startsOn, @NotNull LocalDate endsOn) { }
    public record ObservationRequest(@NotNull Long classId, @NotNull Long studentId,
            @NotNull ObservationKind kind, @NotNull @PastOrPresent LocalDate observedOn,
            @NotBlank @Size(max=2000) String description, @NotNull @Size(max=2000) String action,
            boolean sharedWithFamily) { }
    public record ObservationDecision(@NotNull @Size(max=2000) String action,
            boolean sharedWithFamily, boolean resolved) { }
    public record DocumentRequest(@NotNull DocumentKind kind, @NotBlank @Size(max=1000) String reason) { }
    public record DocumentDecision(@NotNull RequestStatus status, @NotBlank @Size(max=2000) String response) { }
    public record BookRequest(@NotBlank @Size(max=200) String title, @NotNull @Size(max=160) String author,
            @NotBlank @Size(max=80) String reference, @Min(1) @Max(10000) int copies) { }
    public record LoanRequest(@NotNull Long bookId, @NotNull Long studentId, @NotNull @FutureOrPresent LocalDate dueOn) { }
    public record HomeworkDecision(@NotNull HomeworkStatus status, @NotNull @Size(max=2000) String feedback) { }

    public record StudentChoice(Long id, String name, Long classId, String className) { }
    public record Event(Long id, Long classId, String className, String title, String description,
            String kind, LocalDate startsOn, LocalDate endsOn) { }
    public record Observation(Long id, Long studentId, String studentName, String kind, LocalDate observedOn,
            String description, String action, boolean sharedWithFamily, boolean resolved) { }
    public record Document(Long id, Long studentId, String studentName, String kind, String reason,
            String status, String response, LocalDateTime createdAt, boolean mine) { }
    public record Book(Long id, String title, String author, String reference, int copies, int available) { }
    public record Loan(Long id, Long bookId, String title, Long studentId, String studentName,
            LocalDate borrowedOn, LocalDate dueOn, LocalDate returnedOn) { }
    public record Homework(Long postId, Long studentId, String studentName, String title, String content,
            LocalDate dueOn, String status, String feedback, boolean editable) { }
    public record Overview(List<StudentChoice> students, List<Event> events, List<Observation> observations,
            List<Document> requests, List<Book> books, List<Loan> loans, List<Homework> homeworks) { }
}
