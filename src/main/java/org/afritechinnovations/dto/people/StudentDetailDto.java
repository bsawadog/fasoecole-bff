package org.afritechinnovations.dto.people;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public record StudentDetailDto(
        Long studentId,
        Long userId,
        String firstName,
        String lastName,
        String email,
        String phone,
        String registrationNumber,
        LocalDate birthDate,
        String gender,
        String schoolName,
        Long schoolId,
        String className,
        List<ClassRosterRowDto.ParentInfo> parents,
        List<GradeInfo> grades,
        Double overallAverage,
        AttendanceSummary attendanceSummary,
        List<AttendanceInfo> recentAttendance,
        List<InvoiceInfo> invoices,
        BillingSummary billingSummary
) {
    public record GradeInfo(
            Long id,
            String subjectName,
            String teacherName,
            String term,
            String type,
            BigDecimal value,
            BigDecimal maxValue,
            LocalDate gradeDate
    ) {
    }

    public record AttendanceSummary(
            long totalRecords,
            long presentCount,
            long absentCount,
            long lateCount,
            long justifiedAbsences,
            long unjustifiedAbsences,
            double attendanceRate
    ) {
    }

    public record AttendanceInfo(
            Long id,
            LocalDate attendanceDate,
            String status,
            String justification
    ) {
    }

    public record PaymentInfo(
            Long id,
            BigDecimal amount,
            LocalDate paymentDate,
            String method,
            String reference
    ) {
    }

    public record InvoiceInfo(
            Long id,
            Long feeTypeId,
            String feeTypeName,
            BigDecimal amountDue,
            LocalDate dueDate,
            String status,
            BigDecimal totalPaid,
            BigDecimal balance,
            List<PaymentInfo> payments,
            BigDecimal discountAmount,
            String discountReason
    ) {
    }

    public record BillingSummary(
            BigDecimal totalDue,
            BigDecimal totalPaid,
            BigDecimal totalBalance
    ) {
    }
}

