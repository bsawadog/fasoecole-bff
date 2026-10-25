package org.afritechinnovations.service.common;

import org.afritechinnovations.repository.common.SchoolRepository;
import org.afritechinnovations.security.SchoolPermissions;
import org.afritechinnovations.service.academic.OwnerGradeService;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;
import java.math.BigDecimal;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

/** Only an explicitly supplied, isolated local test database is accepted. Tables are connection-local. */
@EnabledIfSystemProperty(named="dashboardChartTestUrl", matches="jdbc:postgresql://127\\.0\\.0\\.1:[0-9]+/dashboard_chart_test")
class DashboardMonthlyFinancePostgresTest {
    SingleConnectionDataSource source;
    JdbcTemplate jdbc;
    OwnerDashboardService service;
    @BeforeEach void setup() {
        source = new SingleConnectionDataSource(System.getProperty("dashboardChartTestUrl") + "?connectTimeout=10&socketTimeout=20","chart_test","chart_test_only",true);
        jdbc = new JdbcTemplate(source);
        service = new OwnerDashboardService(jdbc,mock(SchoolPermissions.class),mock(SchoolRepository.class),mock(OwnerGradeService.class),new DashboardChartQueries(jdbc));
        jdbc.execute("CREATE TEMP TABLE academic_years(id BIGINT,school_id BIGINT,label TEXT,start_date DATE,end_date DATE)");
        jdbc.execute("CREATE TEMP TABLE students(id BIGINT,school_id BIGINT)");
        jdbc.execute("CREATE TEMP TABLE invoices(id BIGINT,student_id BIGINT,status TEXT)");
        jdbc.execute("CREATE TEMP TABLE payments(id BIGINT,invoice_id BIGINT,amount NUMERIC(12,2),payment_date DATE)");
        jdbc.execute("CREATE TEMP TABLE expenses(id BIGINT,school_id BIGINT,amount NUMERIC(12,2),expense_date DATE,managed_payment BOOLEAN)");
        jdbc.execute("CREATE TEMP TABLE teachers(id BIGINT,school_id BIGINT)");
        jdbc.execute("CREATE TEMP TABLE teacher_payments(id BIGINT,teacher_id BIGINT,amount NUMERIC(12,2),payment_date DATE)");
        jdbc.execute("CREATE TEMP TABLE school_payable_payments(teacher_payment_id BIGINT,amount NUMERIC(12,2))");
        jdbc.execute("CREATE TEMP TABLE school_payables(school_id BIGINT,amount NUMERIC(12,2))");
        jdbc.update("INSERT INTO academic_years VALUES(1,10,'2025-2026','2025-09-15','2026-01-10'),(2,20,'Autre école','2025-09-15','2026-01-10'),(3,10,'Sans mouvement','2027-09-01','2028-06-30')");
        jdbc.update("INSERT INTO students VALUES(100,10),(200,20)");
        jdbc.update("INSERT INTO invoices VALUES(1000,100,'CANCELLED'),(2000,200,'PAID')");
        jdbc.update("INSERT INTO payments VALUES(1,1000,9999,'2025-09-14'),(2,1000,500,'2025-09-15'),(3,1000,300,'2025-10-02'),(4,1000,200,'2026-01-10'),(5,1000,9999,'2026-01-11'),(6,2000,100000,'2025-10-02')");
        jdbc.update("INSERT INTO expenses VALUES(1,10,600,'2025-09-16',FALSE),(2,10,400,'2025-10-10',TRUE),(3,10,9999,'2026-01-11',FALSE),(4,20,100000,'2025-10-02',FALSE)");
        jdbc.update("INSERT INTO teachers VALUES(100,10),(200,20)");
        jdbc.update("INSERT INTO teacher_payments VALUES(1,100,250,'2025-10-20'),(2,100,50,'2026-01-03'),(3,200,100000,'2025-10-02')");
        jdbc.update("INSERT INTO school_payable_payments VALUES(1,250)");
        jdbc.update("INSERT INTO school_payables VALUES(10,9000)");
    }
    @AfterEach void close() { if (source != null) source.destroy(); }

    @Test void groupsActualMovementsAcrossCalendarYearsWithEmptyMonthsAndExactDateBounds() {
        var result = service.monthlyFinance(10L,1L);
        assertEquals("2025-2026", result.yearLabel());
        assertEquals(List.of("2025-09","2025-10","2025-11","2025-12","2026-01"),result.months().stream().map(m->m.month()).toList());
        assertEquals(0,new BigDecimal("1000").compareTo(result.receivedAmount()));
        assertEquals(0,new BigDecimal("1300").compareTo(result.spentAmount()));
        assertEquals(0,new BigDecimal("-300").compareTo(result.difference()));
        assertEquals(0,new BigDecimal("650").compareTo(result.months().get(1).spentAmount()));
        assertEquals(0, result.months().get(2).receivedAmount().signum());
        assertEquals(0, result.months().get(2).spentAmount().signum());
    }
    @Test void doesNotCountTheSalaryPaymentBridgeOrUnpaidObligationsAsAdditionalExpenses() {
        var before = service.monthlyFinance(10L,1L);
        jdbc.update("INSERT INTO school_payables VALUES(10,999999)");
        jdbc.update("INSERT INTO school_payable_payments VALUES(2,50)");
        assertEquals(before.spentAmount(), service.monthlyFinance(10L,1L).spentAmount());
    }
    @Test void returnsZerosForAnEmptyYearAndDoesNotExposeAnotherSchoolsYear() {
        var empty = service.monthlyFinance(10L,3L);
        assertEquals(10,empty.months().size());
        assertEquals(0,empty.receivedAmount().signum());
        assertEquals(0,empty.spentAmount().signum());
        assertNull(service.monthlyFinance(10L,2L));
        assertNull(service.monthlyFinance(10L,999L));
    }
}
