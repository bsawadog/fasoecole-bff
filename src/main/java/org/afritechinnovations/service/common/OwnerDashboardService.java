package org.afritechinnovations.service.common;

import lombok.RequiredArgsConstructor;
import org.afritechinnovations.dto.academic.OwnerGradeDto;
import org.afritechinnovations.dto.common.OwnerDashboardDto;
import org.afritechinnovations.model.common.School;
import org.afritechinnovations.repository.common.SchoolRepository;
import org.afritechinnovations.service.academic.OwnerGradeService;
import org.afritechinnovations.model.common.StaffModule;
import org.afritechinnovations.security.SchoolPermissions;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.annotation.Isolation;

import java.math.BigDecimal;
import java.sql.Date;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
public class OwnerDashboardService {

    private final JdbcTemplate jdbcTemplate;
    private final SchoolPermissions permissions;
    private final SchoolRepository schoolRepository;
    private final OwnerGradeService ownerGradeService;
    private final DashboardChartQueries charts;

    public OwnerDashboardDto getDashboard(Long schoolId, Long ownerId) {
        School school = schoolRepository.findById(schoolId)
                .orElseThrow(() -> new IllegalArgumentException("Établissement introuvable"));
        if (!school.getOwner().getId().equals(ownerId)
                && !permissions.staffAllows(school.getId(), ownerId, StaffModule.DASHBOARD)) {
            throw new AccessDeniedException("Vous ne pouvez consulter que vos propres établissements");
        }
        Long selectedYear = org.afritechinnovations.service.academic.SelectedAcademicYear.id(schoolId);
        if (selectedYear == null) selectedYear = jdbcTemplate.query("SELECT id FROM academic_years WHERE school_id=? AND is_current=TRUE", (r,n) -> r.getLong(1), schoolId).stream().findFirst().orElse(-1L);
        final Long yearId = selectedYear;
        LocalDate today = org.afritechinnovations.service.academic.SelectedAcademicYear.viewDate(schoolId, LocalDate.now());
        OwnerGradeDto.SchoolSummary results = ownerGradeService.dashboardSummary(schoolId, ownerId, today)
                .orElse(null);
        return new OwnerDashboardDto(
                school.getId(),
                school.getName(),
                school.getType(),
                today,
                count("SELECT COUNT(DISTINCT student_id) FROM student_enrollments WHERE academic_year_id = ? AND status IN ('ACTIVE','COMPLETED','GRADUATED')", yearId),
                count("""
                        SELECT COUNT(DISTINCT t.id)
                        FROM teachers t JOIN users u ON u.id = t.user_id
                        WHERE t.school_id = ? AND u.active = TRUE AND u.approved = TRUE
                          AND EXISTS(SELECT 1 FROM class_subject_teacher a JOIN classes c ON c.id=a.class_id WHERE a.teacher_id=t.id AND c.academic_year_id=?)
                        """, schoolId, yearId),
                count("SELECT COUNT(*) FROM classes WHERE school_id = ? AND academic_year_id = ?", schoolId, yearId),
                count("SELECT COUNT(*) FROM levels WHERE school_id = ?", schoolId),
                count("""
                        SELECT COUNT(DISTINCT su.user_id)
                        FROM school_users su
                        JOIN roles r ON r.id = su.role_id
                        JOIN users u ON u.id = su.user_id
                        WHERE su.school_id = ? AND r.name = 'PARENT'
                          AND u.active = TRUE AND u.approved = TRUE
                        """, schoolId),
                count("SELECT COUNT(*) FROM users WHERE requested_school_id = ? AND approved = FALSE", schoolId),
                count("""
                        SELECT COUNT(*)
                        FROM invoices i JOIN students s ON s.id = i.student_id
                        WHERE s.school_id = ? AND i.academic_year_id = ? AND i.status <> 'CANCELLED'
                          AND i.amount_due - COALESCE(i.discount_amount,0) >
                              COALESCE((SELECT SUM(p.amount) FROM payments p WHERE p.invoice_id=i.id),0)
                        """, schoolId, yearId),
                decimal("""
                        SELECT COALESCE(SUM(GREATEST(i.amount_due - COALESCE(i.discount_amount,0) - COALESCE(paid.amount, 0), 0)), 0)
                        FROM invoices i
                        JOIN students s ON s.id = i.student_id
                        LEFT JOIN (
                            SELECT invoice_id, SUM(amount) AS amount
                            FROM payments
                            GROUP BY invoice_id
                        ) paid ON paid.invoice_id = i.id
                        WHERE s.school_id = ? AND i.academic_year_id = ? AND i.status <> 'CANCELLED'
                        """, schoolId, yearId),
                decimal("""
                        SELECT COALESCE(SUM(p.amount), 0)
                        FROM payments p
                        JOIN invoices i ON i.id = p.invoice_id
                        JOIN students s ON s.id = i.student_id
                        WHERE s.school_id = ? AND p.payment_date BETWEEN (SELECT start_date FROM academic_years WHERE id=?) AND (SELECT end_date FROM academic_years WHERE id=?)
                        """, schoolId, yearId, yearId),
                decimal("""
                        SELECT COALESCE(SUM(GREATEST(i.amount_due - COALESCE(i.discount_amount,0),0)), 0)
                        FROM invoices i
                        JOIN students s ON s.id = i.student_id
                        WHERE s.school_id = ? AND i.academic_year_id = ? AND i.status <> 'CANCELLED'
                        """, schoolId, yearId),
                attendanceCount(schoolId, yearId, today, null),
                attendanceCount(schoolId, yearId, today, "PRESENT"),
                attendanceCount(schoolId, yearId, today, "ABSENT"),
                attendanceCount(schoolId, yearId, today, "LATE"),
                attendanceCount(schoolId, yearId, today, "EXCUSED"),
                count("""
                        SELECT COUNT(*)
                        FROM report_cards rc JOIN students s ON s.id = rc.student_id
                        WHERE s.school_id = ? AND rc.academic_year_id = ? AND rc.validated = TRUE
                        """, schoolId, yearId),
                results == null ? null : results.average(),
                results == null ? null : results.period().name(),
                results == null ? null : results.passRate(),
                results == null ? 0 : results.rankedCount(),
                canViewChart(school,ownerId,StaffModule.MESSAGES) ? count("WITH params AS (SELECT ?::bigint school_id,?::bigint user_id) "
                        + org.afritechinnovations.service.communication.UnreadMessageSql.SCHOOL_BODY,schoolId,ownerId) : 0,
                recentPayments(schoolId, yearId),
                recentNotifications(ownerId),
                handledAttendanceReports(schoolId, today),
                count("""
                        SELECT COUNT(*) FROM absence_reports
                        WHERE school_id = ? AND status = 'PENDING'
                          AND start_date <= ? AND end_date >= ?
                        """, schoolId, Date.valueOf(today), Date.valueOf(today)),
                pendingAttendanceReports(schoolId, today),
                canViewMonthlyFinance(school, ownerId) ? monthlyFinance(schoolId, yearId) : null,
                canViewChart(school,ownerId,StaffModule.FINANCE) ? charts.receivables(schoolId,yearId,LocalDate.now()) : null,
                canViewChart(school,ownerId,StaffModule.STUDENTS) ? charts.attendance(schoolId,yearId,LocalDate.now()) : null,
                canViewChart(school,ownerId,StaffModule.FINANCE) ? charts.bankPosition(schoolId,yearId,LocalDate.now()) : null
        );
    }

    private boolean canViewMonthlyFinance(School school, Long actor) {
        return school.getOwner().getId().equals(actor)
                || permissions.staffAllows(school.getId(), actor, StaffModule.FINANCE)
                && permissions.staffAllows(school.getId(), actor, StaffModule.EXPENSES);
    }

    private boolean canViewChart(School school,Long actor,StaffModule module) {
        return school.getOwner().getId().equals(actor) || permissions.staffAllows(school.getId(),actor,module);
    }

    private record FinanceRow(Long yearId, String label, LocalDate start, LocalDate end, OwnerDashboardDto.FinanceMonth month) { }

    OwnerDashboardDto.MonthlyFinance monthlyFinance(Long schoolId, Long yearId) {
        var rows = jdbcTemplate.query("""
                WITH selected_year AS (
                    SELECT id, label, start_date, end_date FROM academic_years WHERE id = ? AND school_id = ?
                ), months AS (
                    SELECT generate_series(date_trunc('month', start_date), date_trunc('month', end_date), interval '1 month')::date AS month
                    FROM selected_year
                ), movements AS (
                    SELECT date_trunc('month', p.payment_date)::date AS month, p.amount AS received, 0::numeric AS spent
                    FROM payments p JOIN invoices i ON i.id = p.invoice_id JOIN students s ON s.id = i.student_id
                    CROSS JOIN selected_year y
                    WHERE s.school_id = ? AND p.payment_date BETWEEN y.start_date AND y.end_date
                    UNION ALL
                    SELECT date_trunc('month', e.expense_date)::date, 0::numeric, e.amount
                    FROM expenses e CROSS JOIN selected_year y
                    WHERE e.school_id = ? AND e.expense_date BETWEEN y.start_date AND y.end_date
                    UNION ALL
                    SELECT date_trunc('month', p.payment_date)::date, 0::numeric, p.amount
                    FROM teacher_payments p JOIN teachers t ON t.id = p.teacher_id CROSS JOIN selected_year y
                    WHERE t.school_id = ? AND p.payment_date BETWEEN y.start_date AND y.end_date
                ), totals AS (
                    SELECT month, SUM(received) AS received, SUM(spent) AS spent FROM movements GROUP BY month
                )
                SELECT y.id, y.label, y.start_date, y.end_date, m.month,
                       COALESCE(t.received, 0) AS received, COALESCE(t.spent, 0) AS spent
                FROM selected_year y CROSS JOIN months m LEFT JOIN totals t ON t.month = m.month
                ORDER BY m.month
                """, (r, n) -> new FinanceRow(r.getLong("id"), r.getString("label"),
                r.getDate("start_date").toLocalDate(), r.getDate("end_date").toLocalDate(),
                new OwnerDashboardDto.FinanceMonth(r.getDate("month").toLocalDate().toString().substring(0, 7),
                        r.getBigDecimal("received"), r.getBigDecimal("spent"))), yearId, schoolId, schoolId, schoolId, schoolId);
        if (rows.isEmpty()) return null;
        var first = rows.getFirst();
        var months = rows.stream().map(FinanceRow::month).toList();
        BigDecimal received = months.stream().map(OwnerDashboardDto.FinanceMonth::receivedAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal spent = months.stream().map(OwnerDashboardDto.FinanceMonth::spentAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
        return new OwnerDashboardDto.MonthlyFinance(first.yearId(), first.label(), first.start(), first.end(), months,
                received, spent, received.subtract(spent));
    }

    private List<OwnerDashboardDto.HandledAttendanceReport> handledAttendanceReports(Long schoolId, LocalDate date) {
        return jdbcTemplate.query("""
                SELECT r.id, CONCAT(s.first_name, ' ', s.last_name) AS student_name,
                       r.attendance_type, r.start_date, r.end_date, r.handled_at
                FROM absence_reports r
                JOIN students s ON s.id = r.student_id
                JOIN users u ON u.id = s.user_id
                WHERE r.school_id = ? AND r.status = 'ACKNOWLEDGED'
                  AND r.start_date <= ? AND r.end_date >= ?
                ORDER BY r.handled_at DESC NULLS LAST, r.id DESC
                LIMIT 10
                """, (result, rowNumber) -> new OwnerDashboardDto.HandledAttendanceReport(
                result.getLong("id"), result.getString("student_name"), result.getString("attendance_type"),
                result.getDate("start_date").toLocalDate(), result.getDate("end_date").toLocalDate(),
                result.getObject("handled_at", LocalDateTime.class)
        ), schoolId, Date.valueOf(date), Date.valueOf(date));
    }

    private List<OwnerDashboardDto.PendingAttendanceReport> pendingAttendanceReports(Long schoolId, LocalDate date) {
        return jdbcTemplate.query("""
                SELECT r.id, CONCAT(s.first_name, ' ', s.last_name) AS student_name,
                       r.attendance_type, r.start_date, r.end_date, r.created_at
                FROM absence_reports r
                JOIN students s ON s.id = r.student_id
                JOIN users u ON u.id = s.user_id
                WHERE r.school_id = ? AND r.status = 'PENDING'
                  AND r.start_date <= ? AND r.end_date >= ?
                ORDER BY r.created_at DESC, r.id DESC
                LIMIT 10
                """, (result, rowNumber) -> new OwnerDashboardDto.PendingAttendanceReport(
                result.getLong("id"), result.getString("student_name"), result.getString("attendance_type"),
                result.getDate("start_date").toLocalDate(), result.getDate("end_date").toLocalDate(),
                result.getObject("created_at", LocalDateTime.class)
        ), schoolId, Date.valueOf(date), Date.valueOf(date));
    }

    private long count(String sql, Object... arguments) {
        Long result = jdbcTemplate.queryForObject(sql, Long.class, arguments);
        return result == null ? 0 : result;
    }

    private long attendanceCount(Long schoolId, Long yearId, LocalDate date, String status) {
        String sql = """
                SELECT COUNT(*)
                FROM attendances a JOIN classes c ON c.id = a.class_id
                WHERE c.school_id = ? AND c.academic_year_id = ? AND a.attendance_date = ?
                """ + (status == null ? "" : " AND a.status = ?");
        return status == null
                ? count(sql, schoolId, yearId, Date.valueOf(date))
                : count(sql, schoolId, yearId, Date.valueOf(date), status);
    }

    private BigDecimal decimal(String sql, Object... arguments) {
        BigDecimal result = jdbcTemplate.queryForObject(sql, BigDecimal.class, arguments);
        return result == null ? BigDecimal.ZERO : result;
    }

    private List<OwnerDashboardDto.RecentPayment> recentPayments(Long schoolId, Long yearId) {
        return jdbcTemplate.query("""
                SELECT p.id, CONCAT(s.first_name, ' ', s.last_name) AS student_name,
                       p.amount, p.payment_date, p.method, p.reference
                FROM payments p
                JOIN invoices i ON i.id = p.invoice_id
                JOIN students s ON s.id = i.student_id
                JOIN users u ON u.id = s.user_id
                WHERE s.school_id = ? AND EXISTS (
                    SELECT 1 FROM academic_years y WHERE y.id = ?
                    AND p.payment_date BETWEEN y.start_date AND y.end_date)
                ORDER BY p.payment_date DESC, p.id DESC
                LIMIT 5
                """, (result, rowNumber) -> new OwnerDashboardDto.RecentPayment(
                result.getLong("id"),
                result.getString("student_name"),
                result.getBigDecimal("amount"),
                result.getDate("payment_date").toLocalDate(),
                result.getString("method"),
                result.getString("reference")
        ), schoolId, yearId);
    }

    private List<OwnerDashboardDto.RecentNotification> recentNotifications(Long ownerId) {
        return jdbcTemplate.query("""
                SELECT id, title, content, is_read, created_at
                FROM notifications
                WHERE user_id = ?
                ORDER BY created_at DESC, id DESC
                LIMIT 5
                """, (result, rowNumber) -> new OwnerDashboardDto.RecentNotification(
                result.getLong("id"),
                result.getString("title"),
                result.getString("content"),
                result.getBoolean("is_read"),
                result.getObject("created_at", LocalDateTime.class)
        ), ownerId);
    }
}
