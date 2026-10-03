package org.afritechinnovations.dto.people;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/** Contrats de réponse de l'API /api/teacher-work (gestion du travail et de la paie des enseignants). */
public final class TeacherWorkDto {

    private TeacherWorkDto() {
    }

    public record TeacherInfo(Long id, Long schoolId, String firstName, String lastName, String email, String phone,
                              String specialty, List<String> subjects, Boolean activeInClass, long classCount,
                              Long userId, Boolean emailVerified, String invitationDeliveryStatus, String employeeNumber) {
    }

    public record TeacherDetail(TeacherInfo teacher, String rateType, BigDecimal rate,
                                List<SlotInfo> schedule, MonthSummary month) {
    }

    public record SlotInfo(Long id, Long classId, String className, int dayOfWeek, String startTime,
                           String endTime, LocalDate effectiveFrom, LocalDate effectiveTo) {
    }

    public record MonthSummary(BigDecimal plannedHours, BigDecimal workedHours, BigDecimal absenceHours,
                               BigDecimal extraHours, BigDecimal amountDue, BigDecimal paid, BigDecimal remaining,
                               List<ClassBreakdown> perClass, List<SessionInfo> sessions,
                               List<ExtraInfo> extras, List<PaymentInfo> payments) {
    }

    public record ClassBreakdown(Long classId, String className, BigDecimal plannedHours, BigDecimal workedHours,
                                 BigDecimal absenceHours, BigDecimal extraHours) {
    }

    public record SessionInfo(Long slotId, LocalDate date, Long classId, String className, String startTime,
                              String endTime, String status, BigDecimal hours) {
    }

    public record ExtraInfo(Long id, Long classId, String className, LocalDate date, BigDecimal hours,
                            String description) {
    }

    public record PaymentInfo(Long id, LocalDate date, BigDecimal amount, String reference) {
    }

    public record RateInfo(Long id, String type, BigDecimal amount, LocalDate effectiveFrom) {
    }
}
