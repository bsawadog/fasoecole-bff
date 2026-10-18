package org.afritechinnovations.repository.finance;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;
import java.util.List;

@Repository
@RequiredArgsConstructor
public class PayrollTaxRepository {
    private final JdbcTemplate jdbc;

    public boolean hasAutomaticCalculation(Long schoolId,Long teacherId,LocalDate period) {
        return Boolean.TRUE.equals(jdbc.queryForObject("SELECT EXISTS(SELECT 1 FROM school_payables WHERE school_id=? AND teacher_id=? AND period=? AND payroll_calculation IS NOT NULL)",Boolean.class,schoolId,teacherId,period));
    }

    public void assertCalculationCurrent(Long schoolId, Long teacherId, LocalDate period, BigDecimal gross) {
        var amounts=jdbc.query("SELECT payroll_calculated_gross FROM school_payables WHERE school_id=? AND teacher_id=? AND period=? AND payroll_calculation IS NOT NULL",
                (rs,n)->rs.getBigDecimal(1),schoolId,teacherId,period);
        if(amounts.stream().anyMatch(amount->amount!=null && amount.compareTo(gross)!=0))
            throw new IllegalArgumentException("La rémunération a changé depuis le calcul fiscal. Recalculez et validez la paie avant le versement ou l’édition du bulletin.");
    }

    public Optional<BigDecimal> teacherIuts(Long schoolId, Long teacherId, LocalDate period) {
        return jdbc.query("SELECT payroll_iuts + payroll_deductions FROM school_payables WHERE school_id=? AND teacher_id=? AND period=? AND source='TEACHER_SALARY'",
                (rs,n) -> rs.getBigDecimal(1),schoolId,teacherId,period).stream().findFirst();
    }
    public List<LocalDate> teacherTaxMonths(Long schoolId, Long teacherId, LocalDate from) {
        return jdbc.query("SELECT period FROM school_payables WHERE school_id=? AND teacher_id=? AND period>=? AND source='TEACHER_SALARY' AND (payroll_iuts>0 OR payroll_deductions>0)",
                (rs,n) -> rs.getDate(1).toLocalDate(),schoolId,teacherId,from);
    }
}
