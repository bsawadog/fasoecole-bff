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
        List<PendingAttendanceReport> pendingAttendanceReports,
        MonthlyFinance monthlyFinance,
        ClassReceivables classReceivables,
        WeeklyAttendance weeklyAttendance,
        BankPosition bankPosition
) {
    public record BankPosition(String yearLabel,LocalDate fromDate,LocalDate asOf,BigDecimal openingBalance,BigDecimal receipts,BigDecimal payments,BigDecimal balance) {}
    public record ClassReceivables(Long academicYearId,String yearLabel,LocalDate asOf,List<ClassReceivable> classes,
                                   BigDecimal expected,BigDecimal received,BigDecimal overdue,BigDecimal upcoming,BigDecimal credit) { }
    public record ClassReceivable(Long classId,String label,BigDecimal expected,BigDecimal received,
                                  BigDecimal overdue,BigDecimal upcoming,BigDecimal credit) { }
    public record WeeklyAttendance(Long academicYearId,String yearLabel,LocalDate fromDate,LocalDate toDate,
                                   List<AttendanceWeek> weeks) { }
    public record AttendanceWeek(LocalDate fromDate,LocalDate toDate,long present,long absent,long late,long excused,
                                 long missing,long observedCalls) { }
    public record MonthlyFinance(Long academicYearId, String yearLabel, LocalDate startDate, LocalDate endDate,
                                 List<FinanceMonth> months, BigDecimal receivedAmount,
                                 BigDecimal spentAmount, BigDecimal difference) { }
    public record FinanceMonth(String month, BigDecimal receivedAmount, BigDecimal spentAmount) { }
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
