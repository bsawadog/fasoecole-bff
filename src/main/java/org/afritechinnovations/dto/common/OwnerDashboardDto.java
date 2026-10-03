package org.afritechinnovations.dto.common;

import org.afritechinnovations.model.common.SchoolType;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

public record OwnerDashboardDto(
        Long schoolId,
        String schoolName,
        SchoolType schoolType,
        LocalDate generatedDate,
        long students,
        long teachers,
        long classes,
        long levels,
        long parents,
        long pendingAccountApprovals,
        long pendingInvoices,
        BigDecimal outstandingAmount,
        BigDecimal receivedAmount,
        BigDecimal expectedAmount,
        long attendanceRecorded,
        long presentToday,
        long absentToday,
        long lateToday,
        long excusedToday,
        long validatedReportCards,
        BigDecimal schoolAverage,
        String averagePeriodName,
        BigDecimal passRate,
        int rankedStudents,
        long unreadMessages,
        List<RecentPayment> recentPayments,
        List<RecentNotification> recentNotifications,
        List<HandledAttendanceReport> handledAttendanceReports,
        long pendingAttendanceReportsCount,
        List<PendingAttendanceReport> pendingAttendanceReports
) {
    public record PendingAttendanceReport(
            Long id, String studentName, String attendanceType,
            LocalDate startDate, LocalDate endDate, LocalDateTime createdAt
    ) {}

    public record HandledAttendanceReport(
            Long id, String studentName, String attendanceType,
            LocalDate startDate, LocalDate endDate, LocalDateTime handledAt
    ) {}

    public record RecentPayment(
            Long id,
            String studentName,
            BigDecimal amount,
            LocalDate paymentDate,
            String method,
            String reference
    ) {
    }

    public record RecentNotification(
            Long id,
            String title,
            String content,
            boolean read,
            LocalDateTime createdAt
    ) {
    }
}
