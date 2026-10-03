package org.afritechinnovations.service.academic;

import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import org.afritechinnovations.model.academic.AcademicYear;
import org.afritechinnovations.model.people.EnrollmentStatus;
import org.afritechinnovations.repository.academic.AcademicYearRepository;
import org.afritechinnovations.repository.common.SchoolRepository;
import org.afritechinnovations.repository.people.StudentEnrollmentRepository;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class AcademicYearClosureService {
    private final AcademicYearRepository years;
    private final SchoolRepository schools;
    private final StudentEnrollmentRepository enrollments;
    private final JdbcTemplate jdbc;
    private final org.afritechinnovations.security.SchoolPermissions permissions;

    public record CloseRequest(@NotNull Long fromYearId, @NotNull Long toYearId,
                               @NotNull @jakarta.validation.constraints.DecimalMin("0") @jakarta.validation.constraints.Digits(integer=10,fraction=2) BigDecimal cashBalance,
                               @NotNull @jakarta.validation.constraints.Digits(integer=10,fraction=2) BigDecimal bankBalance) {}
    public record CloseResult(Long closedYearId, Long currentYearId, int debtsCarried) {}

    @Transactional
    public CloseResult close(Long schoolId, CloseRequest request, Long userId, boolean systemAdmin) {
        requireOwner(schoolId, userId, systemAdmin);
        List<AcademicYear> locked = years.lockSchoolYears(schoolId);
        AcademicYear from = find(locked, request.fromYearId());
        AcademicYear to = find(locked, request.toYearId());
        from.requireOpen();
        to.requireOpen();
        if (!Boolean.TRUE.equals(from.getIsCurrent()))
            throw new IllegalArgumentException("Clôturez l'année actuellement en cours.");
        if (!to.getStartDate().isAfter(from.getEndDate()))
            throw new IllegalArgumentException("La nouvelle année doit commencer après la fin de l'ancienne.");
        Long pendingReports = jdbc.queryForObject("SELECT COUNT(*) FROM absence_reports WHERE school_id=? AND status='PENDING' AND start_date<=? AND end_date>=?",Long.class,schoolId,from.getEndDate(),from.getStartDate());
        if (pendingReports != null && pendingReports > 0)
            throw new IllegalArgumentException("Traitez les signalements de présence en attente avant de clôturer cette année.");
        var sourceEnrollments = enrollments.findByYearWithStudent(from.getId());
        if (sourceEnrollments.stream().anyMatch(e -> e.getStatus() == EnrollmentStatus.ACTIVE))
            throw new IllegalArgumentException("Validez le passage, le redoublement ou le départ de chaque élève avant la clôture.");
        for (var entry : sourceEnrollments) {
            if (entry.getStatus() != EnrollmentStatus.COMPLETED) continue;
            if (entry.getDecision() == null) throw new IllegalArgumentException("Une inscription terminée n'a pas de décision de fin d'année.");
            if (entry.getDecision() == org.afritechinnovations.model.people.EnrollmentDecision.PROMOTED
                    || entry.getDecision() == org.afritechinnovations.model.people.EnrollmentDecision.REPEATED) {
                var target = enrollments.findByStudentIdAndAcademicYearId(entry.getStudent().getId(),to.getId()).stream()
                        .filter(e -> e.getStatus()==EnrollmentStatus.ACTIVE).toList();
                if (target.size()!=1 || !target.get(0).getSchoolClass().getSchool().getId().equals(schoolId))
                    throw new IllegalArgumentException("Un élève admis ou redoublant n'est pas réinscrit dans la nouvelle année sélectionnée.");
            }
        }
        if (from.getNextYearId() != null || locked.stream().anyMatch(y -> to.getId().equals(y.getNextYearId())))
            throw new IllegalArgumentException("Cette année est déjà liée à une clôture.");

        // Carry old outstanding invoices as references, including earlier-year carryovers.
        int carried = jdbc.update("""
            INSERT INTO academic_year_receivables(academic_year_id,invoice_id,amount_at_closure)
            SELECT ?,i.id,i.amount_due-COALESCE(i.discount_amount,0)-COALESCE(p.paid,0)
            FROM invoices i JOIN students s ON s.id=i.student_id
            LEFT JOIN (SELECT invoice_id,SUM(amount) paid FROM payments GROUP BY invoice_id) p ON p.invoice_id=i.id
            WHERE s.school_id=? AND i.status<>'CANCELLED' AND i.amount_due-COALESCE(i.discount_amount,0)-COALESCE(p.paid,0)>0
              AND (i.academic_year_id=? OR EXISTS(SELECT 1 FROM academic_year_receivables r WHERE r.academic_year_id=? AND r.invoice_id=i.id))
            """, to.getId(), schoolId, from.getId(), from.getId());
        jdbc.update("INSERT INTO academic_year_balances(academic_year_id,account,opening_balance) VALUES (?,'CASH',?),(?,'BANK',?)",
                to.getId(), request.cashBalance(), to.getId(), request.bankBalance());
        from.setIsCurrent(false);
        from.setClosedAt(LocalDateTime.now());
        from.setClosedBy(userId);
        from.setNextYearId(to.getId());
        locked.forEach(y -> y.setIsCurrent(y.getId().equals(to.getId())));
        years.saveAll(locked);
        years.flush();
        return new CloseResult(from.getId(), to.getId(), carried);
    }

    @Transactional(readOnly = true)
    public Map<String, Object> balances(Long schoolId, Long yearId, Long userId, boolean systemAdmin) {
        var school = schools.findById(schoolId).orElseThrow();
        if (!systemAdmin && (school.getOwner()==null || !school.getOwner().getId().equals(userId))
                && !permissions.staffAllows(schoolId,userId,org.afritechinnovations.model.common.StaffModule.FINANCE))
            throw new AccessDeniedException("Accès réservé à la gestion financière.");
        find(years.findBySchoolId(schoolId), yearId);
        return Map.of("accounts", jdbc.queryForList("SELECT account,opening_balance FROM academic_year_balances WHERE academic_year_id=?",yearId),
                "receivables", jdbc.queryForList("""
                    SELECT r.invoice_id,r.amount_at_closure,s.registration_number,u.first_name,u.last_name,
                      GREATEST(i.amount_due-COALESCE(i.discount_amount,0)-COALESCE(p.paid,0),0) remaining
                    FROM academic_year_receivables r JOIN invoices i ON i.id=r.invoice_id
                    JOIN students s ON s.id=i.student_id JOIN users u ON u.id=s.user_id
                    LEFT JOIN (SELECT invoice_id,SUM(amount) paid FROM payments GROUP BY invoice_id) p ON p.invoice_id=i.id
                    WHERE r.academic_year_id=? AND s.school_id=? ORDER BY u.last_name,u.first_name
                    """,yearId,schoolId));
    }

    private void requireOwner(Long schoolId, Long userId, boolean systemAdmin) {
        var school = schools.findById(schoolId).orElseThrow(() -> new IllegalArgumentException("Établissement introuvable."));
        if (!systemAdmin && (school.getOwner()==null || !school.getOwner().getId().equals(userId)))
            throw new AccessDeniedException("La clôture est réservée au propriétaire de l'établissement.");
    }
    private AcademicYear find(List<AcademicYear> values, Long id) {
        return values.stream().filter(y -> y.getId().equals(id)).findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Cette année n'appartient pas à l'établissement."));
    }
}
