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
        long attendanceRecorded,
        long presentToday,
        long absentToday,
        long lateToday,
        long excusedToday,
        long validatedReportCards,
        BigDecimal schoolAverage,
        long unreadMessages,
        List<RecentPayment> recentPayments,
        List<RecentNotification> recentNotifications
) {
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
