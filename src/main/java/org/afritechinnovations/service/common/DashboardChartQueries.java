package org.afritechinnovations.service.common;

import lombok.RequiredArgsConstructor;
import org.afritechinnovations.dto.common.OwnerDashboardDto.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.DayOfWeek;
import java.time.temporal.TemporalAdjusters;
import java.util.List;
import java.util.function.Function;

@Component @RequiredArgsConstructor
public class DashboardChartQueries {
    private final JdbcTemplate jdbc;
    private record Year(Long id,String label,LocalDate start,LocalDate end) { }
    private Year year(Long school,Long id) {
        return jdbc.query("SELECT id,label,start_date,end_date FROM academic_years WHERE school_id=? AND id=?",
                (r,n)->new Year(r.getLong("id"),r.getString("label"),r.getDate("start_date").toLocalDate(),r.getDate("end_date").toLocalDate()),school,id).stream().findFirst().orElse(null);
    }
    public BankPosition bankPosition(Long school,Long yearId,LocalDate today) {
        Year year=year(school,yearId);if(year==null || today.isBefore(year.start()))return null;
        LocalDate end=today.isBefore(year.end())?today:year.end();
        var opening=jdbc.query("SELECT opening_balance FROM academic_year_balances WHERE academic_year_id=? AND account='BANK'",(r,n)->r.getBigDecimal(1),yearId).stream().findFirst().orElse(null);
        return jdbc.query("""
            WITH scope AS (SELECT ?::bigint school_id,?::date first_day,?::date last_day),
            incoming AS (
                SELECT COALESCE(SUM(p.amount),0) amount FROM payments p JOIN invoices i ON i.id=p.invoice_id
                JOIN academic_years y ON y.id=i.academic_year_id CROSS JOIN scope s
                WHERE y.school_id=s.school_id AND p.method='BANK_TRANSFER' AND p.payment_date BETWEEN s.first_day AND s.last_day
            ), outgoing AS (
                SELECT e.amount FROM expenses e CROSS JOIN scope s
                WHERE e.school_id=s.school_id AND e.payment_method='BANK_TRANSFER' AND e.expense_date BETWEEN s.first_day AND s.last_day
                UNION ALL
                SELECT t.amount FROM teacher_payments t JOIN teachers teacher ON teacher.id=t.teacher_id CROSS JOIN scope s
                WHERE teacher.school_id=s.school_id AND t.payment_date BETWEEN s.first_day AND s.last_day
                  AND EXISTS(SELECT 1 FROM school_payable_payments p WHERE p.teacher_payment_id=t.id AND p.school_id=s.school_id AND p.method='BANK_TRANSFER')
            )
            SELECT incoming.amount receipts,(SELECT COALESCE(SUM(amount),0) FROM outgoing) payments FROM incoming
            """,(r,n)->new BankPosition(year.label(),year.start(),end,opening,r.getBigDecimal("receipts"),r.getBigDecimal("payments"),
                (opening==null?BigDecimal.ZERO:opening).add(r.getBigDecimal("receipts")).subtract(r.getBigDecimal("payments"))),school,year.start(),end).getFirst();
    }
    public ClassReceivables receivables(Long school,Long yearId,LocalDate asOf) {
        Year year=year(school,yearId);if(year==null)return null;
        List<ClassReceivable> rows=jdbc.query("""
            WITH params AS (SELECT ?::bigint school_id,?::bigint year_id,?::date as_of),
            class_scope AS (SELECT c.id,c.name FROM classes c,params p WHERE c.school_id=p.school_id AND c.academic_year_id=p.year_id),
            enrolled AS (
                SELECT DISTINCT ON (e.student_id) e.student_id,e.class_id FROM student_enrollments e
                JOIN class_scope c ON c.id=e.class_id CROSS JOIN params p
                WHERE e.academic_year_id=p.year_id ORDER BY e.student_id,e.enrollment_date DESC,e.id DESC
            ), billed AS (
                SELECT i.id,i.student_id,i.due_date,GREATEST(i.amount_due-COALESCE(i.discount_amount,0),0) net
                FROM invoices i JOIN students s ON s.id=i.student_id CROSS JOIN params p
                WHERE s.school_id=p.school_id AND i.academic_year_id=p.year_id AND i.status<>'CANCELLED'
            ), paid AS (
                SELECT b.id,SUM(payment.amount) amount FROM billed b JOIN payments payment ON payment.invoice_id=b.id CROSS JOIN params p
                WHERE payment.payment_date<=p.as_of GROUP BY b.id
            ), grouped AS (
                SELECT e.class_id,SUM(b.net) expected,SUM(LEAST(COALESCE(paid.amount,0),b.net)) received,
                    SUM(CASE WHEN b.due_date<p.as_of THEN GREATEST(b.net-COALESCE(paid.amount,0),0) ELSE 0 END) overdue,
                    SUM(CASE WHEN b.due_date>=p.as_of THEN GREATEST(b.net-COALESCE(paid.amount,0),0) ELSE 0 END) upcoming,
                    SUM(GREATEST(COALESCE(paid.amount,0)-b.net,0)) credit
                FROM billed b LEFT JOIN paid ON paid.id=b.id LEFT JOIN enrolled e ON e.student_id=b.student_id CROSS JOIN params p GROUP BY e.class_id
            )
            SELECT c.id class_id,c.name label,COALESCE(g.expected,0) expected,COALESCE(g.received,0) received,
                COALESCE(g.overdue,0) overdue,COALESCE(g.upcoming,0) upcoming,COALESCE(g.credit,0) credit
            FROM class_scope c LEFT JOIN grouped g ON g.class_id=c.id
            UNION ALL SELECT NULL,'Sans classe pour cette année',expected,received,overdue,upcoming,credit FROM grouped WHERE class_id IS NULL
            ORDER BY overdue DESC,label
            """,(r,n)->new ClassReceivable((Long)r.getObject("class_id"),r.getString("label"),r.getBigDecimal("expected"),r.getBigDecimal("received"),r.getBigDecimal("overdue"),r.getBigDecimal("upcoming"),r.getBigDecimal("credit")),school,yearId,asOf);
        return new ClassReceivables(year.id(),year.label(),asOf,rows,sum(rows,ClassReceivable::expected),sum(rows,ClassReceivable::received),sum(rows,ClassReceivable::overdue),sum(rows,ClassReceivable::upcoming),sum(rows,ClassReceivable::credit));
    }
    private static BigDecimal sum(List<ClassReceivable> rows,Function<ClassReceivable,BigDecimal> field) {
        return rows.stream().map(field).reduce(BigDecimal.ZERO,BigDecimal::add);
    }
    public WeeklyAttendance attendance(Long school,Long yearId,LocalDate asOf) {
        Year year=year(school,yearId);if(year==null)return null;
        if(asOf.isBefore(year.start()))return new WeeklyAttendance(year.id(),year.label(),year.start(),year.end(),List.of());
        LocalDate end=asOf.isBefore(year.end())?asOf:year.end();
        LocalDate first=end.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)).minusWeeks(7);
        LocalDate from=year.start().isAfter(first)?year.start():first;
        var weeks=jdbc.query("""
            WITH params AS (SELECT ?::bigint school_id,?::bigint year_id,?::date first_day,?::date last_day),
            class_scope AS (SELECT c.id FROM classes c,params p WHERE c.school_id=p.school_id AND c.academic_year_id=p.year_id),
            recorded AS (
                SELECT DISTINCT ON (a.class_id,a.student_id,a.attendance_date) a.class_id,a.student_id,a.attendance_date call_date,a.status
                FROM attendances a JOIN class_scope c ON c.id=a.class_id JOIN students s ON s.id=a.student_id CROSS JOIN params p
                WHERE s.school_id=p.school_id AND a.attendance_date BETWEEN p.first_day AND p.last_day
                ORDER BY a.class_id,a.student_id,a.attendance_date,a.id DESC
            ), calls AS (SELECT DISTINCT class_id,call_date FROM recorded),
            roster AS (
                SELECT e.class_id,e.student_id,MIN(e.enrollment_date) enrolled_on FROM student_enrollments e
                JOIN class_scope c ON c.id=e.class_id JOIN students s ON s.id=e.student_id CROSS JOIN params p
                WHERE s.school_id=p.school_id AND e.academic_year_id=p.year_id AND e.status IN ('ACTIVE','COMPLETED','GRADUATED')
                GROUP BY e.class_id,e.student_id
            ), entries AS (
                SELECT class_id,student_id,call_date,status FROM recorded
                UNION ALL
                SELECT c.class_id,e.student_id,c.call_date,NULL FROM calls c JOIN roster e ON e.class_id=c.class_id AND e.enrolled_on<=c.call_date
                WHERE NOT EXISTS(SELECT 1 FROM recorded r WHERE r.class_id=c.class_id AND r.student_id=e.student_id AND r.call_date=c.call_date)
            ), totals AS (
                SELECT date_trunc('week',call_date)::date week,
                    COUNT(*) FILTER(WHERE status='PRESENT') present,COUNT(*) FILTER(WHERE status='ABSENT') absent,
                    COUNT(*) FILTER(WHERE status='LATE') late,COUNT(*) FILTER(WHERE status='EXCUSED') excused,
                    COUNT(*) FILTER(WHERE status IS NULL) missing FROM entries GROUP BY 1
            ), call_totals AS (SELECT date_trunc('week',call_date)::date week,COUNT(*) total FROM calls GROUP BY 1),
            weeks AS (
                SELECT generate_series(date_trunc('week',first_day),date_trunc('week',last_day),interval '1 week')::date week FROM params
            )
            SELECT GREATEST(w.week,p.first_day) first_day,LEAST(w.week+6,p.last_day) last_day,
                COALESCE(t.present,0) present,COALESCE(t.absent,0) absent,COALESCE(t.late,0) late,
                COALESCE(t.excused,0) excused,COALESCE(t.missing,0) missing,COALESCE(c.total,0) calls
            FROM weeks w CROSS JOIN params p LEFT JOIN totals t ON t.week=w.week LEFT JOIN call_totals c ON c.week=w.week ORDER BY w.week
            """,(r,n)->new AttendanceWeek(r.getDate("first_day").toLocalDate(),r.getDate("last_day").toLocalDate(),r.getLong("present"),r.getLong("absent"),r.getLong("late"),r.getLong("excused"),r.getLong("missing"),r.getLong("calls")),school,yearId,from,end);
        return new WeeklyAttendance(year.id(),year.label(),from,end,weeks);
    }
}
