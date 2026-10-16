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

    public Optional<BigDecimal> teacherIuts(Long schoolId, Long teacherId, LocalDate period) {
        return jdbc.query("SELECT payroll_iuts FROM school_payables WHERE school_id=? AND teacher_id=? AND period=? AND source='TEACHER_SALARY'",
                (rs,n) -> rs.getBigDecimal(1),schoolId,teacherId,period).stream().findFirst();
    }
    public List<LocalDate> teacherTaxMonths(Long schoolId, Long teacherId, LocalDate from) {
        return jdbc.query("SELECT period FROM school_payables WHERE school_id=? AND teacher_id=? AND period>=? AND source='TEACHER_SALARY' AND payroll_iuts>0",
                (rs,n) -> rs.getDate(1).toLocalDate(),schoolId,teacherId,from);
    }
}
