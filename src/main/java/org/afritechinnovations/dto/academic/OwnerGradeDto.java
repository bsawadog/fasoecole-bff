package org.afritechinnovations.dto.academic;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.afritechinnovations.model.academic.GradePeriodStatus;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/** Contrats de l'espace propriétaire « Notes & bulletins ». */
public final class OwnerGradeDto {

    private OwnerGradeDto() {
    }

    // ------------------------------------------------------------------ périodes

    public record PeriodInfo(Long id, Long schoolId, Long academicYearId, String academicYearLabel, String code,
                             String name, LocalDate startDate, LocalDate endDate, BigDecimal passMark,
                             GradePeriodStatus status, LocalDateTime publishedAt, long evaluationCount) {
    }

    public record PeriodRequest(
            @NotNull(message = "L'année scolaire est obligatoire") Long academicYearId,
            @NotBlank(message = "Le code est obligatoire") @Size(max = 20) String code,
            @NotBlank(message = "Le libellé est obligatoire") @Size(max = 80) String name,
            @NotNull(message = "La date de début est obligatoire") LocalDate startDate,
            @NotNull(message = "La date de fin est obligatoire") LocalDate endDate,
            @DecimalMin(value = "0", message = "Seuil invalide")
            @DecimalMax(value = "20", message = "Le seuil de réussite ne peut dépasser 20") BigDecimal passMark) {
    }

    public record DefaultPeriodsRequest(
            @NotNull(message = "L'année scolaire est obligatoire") Long academicYearId,
            @NotBlank(message = "Le découpage est obligatoire") String scheme) {
    }

    public record StatusRequest(@NotNull(message = "Le statut est obligatoire") GradePeriodStatus status) {
    }

    // ------------------------------------------------------------------ matières / coefficients

    public record AssignmentInfo(Long classSubjectTeacherId, Long teacherId, String teacherName, boolean active) {
    }

    public record ClassSubjectInfo(Long subjectId, String subjectName, BigDecimal coefficient,
                                   BigDecimal defaultCoefficient, boolean overridden,
                                   List<AssignmentInfo> assignments) {
    }

    public record CoefficientRequest(
            @NotNull(message = "Le coefficient est obligatoire")
            @DecimalMin(value = "0.25", message = "Le coefficient doit être au moins 0,25")
            @DecimalMax(value = "20", message = "Le coefficient ne peut dépasser 20") BigDecimal coefficient) {
    }

    // ------------------------------------------------------------------ évaluations

    public record EvaluationInfo(Long id, Long classSubjectTeacherId, Long subjectId, String subjectName,
                                 String teacherName, Long periodId, String title, String type, LocalDate evalDate,
                                 BigDecimal maxValue, BigDecimal weight, long gradedCount, long studentCount,
                                 BigDecimal averageOn20) {
    }

    public record EvaluationRequest(
            @NotNull(message = "La matière est obligatoire") Long classSubjectTeacherId,
            @NotNull(message = "La période est obligatoire") Long periodId,
            @NotBlank(message = "L'intitulé est obligatoire") @Size(max = 120) String title,
            @NotBlank(message = "Le type est obligatoire") @Size(max = 30) String type,
            @NotNull(message = "La date est obligatoire") LocalDate evalDate,
            @DecimalMin(value = "1", message = "Le barème doit être au moins 1")
            @DecimalMax(value = "100", message = "Le barème ne peut dépasser 100") BigDecimal maxValue,
            @DecimalMin(value = "0.25", message = "Le poids doit être au moins 0,25")
            @DecimalMax(value = "10", message = "Le poids ne peut dépasser 10") BigDecimal weight) {
    }

    public record SheetRow(Long studentId, String fullName, String registrationNumber, BigDecimal value) {
    }

    public record GradeSheet(EvaluationInfo evaluation, PeriodInfo period, String className, List<SheetRow> rows) {
    }

    public record GradeEntry(@NotNull Long studentId, BigDecimal value) {
    }

    public record SaveGradesRequest(@NotNull @Valid List<GradeEntry> grades, @Size(max = 255) String reason) {
    }

    public record SaveGradesResult(int created, int updated, int deleted, int unchanged) {
    }

    public record HistoryEntry(Long id, Long evaluationId, String evaluationTitle, Long studentId,
                               String studentName, String action, BigDecimal oldValue, BigDecimal newValue,
                               String reason, String changedByName, LocalDateTime changedAt) {
    }

    // ------------------------------------------------------------------ résultats

    public record SubjectColumn(Long subjectId, String subjectName, BigDecimal coefficient) {
    }

    public record StudentResult(Long studentId, String fullName, String registrationNumber,
                                Map<Long, BigDecimal> subjectAverages, BigDecimal average, Integer rank,
                                String mention, Boolean passed, String comment, boolean reportCardGenerated,
                                boolean validated) {
    }

    public record SubjectStat(Long subjectId, String subjectName, BigDecimal coefficient, BigDecimal average,
                              BigDecimal min, BigDecimal max, BigDecimal passRate, int gradedStudents) {
    }

    public record ClassStats(int studentCount, int rankedCount, BigDecimal classAverage, BigDecimal highest,
                             BigDecimal lowest, int passCount, BigDecimal passRate, Map<String, Integer> mentions) {
    }

    public record ClassResults(Long classId, String className, String levelName, PeriodInfo period,
                               List<SubjectColumn> subjects, List<StudentResult> students, ClassStats stats,
                               List<SubjectStat> subjectStats) {
    }

    public record CommentRequest(@Size(max = 255, message = "255 caractères maximum") String comment) {
    }

    public record ClassSummary(Long classId, String className, String levelName, int studentCount,
                               int rankedCount, BigDecimal average, BigDecimal passRate, int reportCards) {
    }

    public record SchoolSummary(PeriodInfo period, List<ClassSummary> classes, BigDecimal average,
                                BigDecimal passRate, int studentCount, int rankedCount) {
    }

    // ------------------------------------------------------------------ bulletins

    public record BulletinLine(String subjectName, String teacherName, BigDecimal coefficient, BigDecimal average,
                               BigDecimal weighted, BigDecimal classAverage, BigDecimal min, BigDecimal max,
                               String appreciation) {
    }

    public record Bulletin(Long studentId, String fullName, String registrationNumber, LocalDate birthDate,
                           String gender, List<BulletinLine> lines, BigDecimal totalCoefficients,
                           BigDecimal totalWeighted, BigDecimal average, Integer rank, int classSize,
                           String mention, String decision, String comment, long absences,
                           long unjustifiedAbsences, long lates, boolean validated) {
    }

    public record BulletinBatch(String schoolName, String schoolAddress, String schoolPhone, String schoolEmail,
                                String academicYearLabel, String periodName, String className, String levelName,
                                BigDecimal passMark, BigDecimal classAverage, BigDecimal highest, BigDecimal lowest,
                                boolean published, List<Bulletin> bulletins) {
    }
}
