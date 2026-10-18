package org.afritechinnovations.service.common;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;
import java.time.LocalDate;
import java.math.BigDecimal;
import static org.junit.jupiter.api.Assertions.*;
@EnabledIfSystemProperty(named="dashboardChartTestUrl",matches="jdbc:postgresql://127\\.0\\.0\\.1:[0-9]+/dashboard_chart_test")
class DashboardChartQueriesPostgresTest {
 SingleConnectionDataSource source; JdbcTemplate jdbc; DashboardChartQueries charts;
 @BeforeEach void setup(){
  source=new SingleConnectionDataSource(System.getProperty("dashboardChartTestUrl"),"chart_test","chart_test_only",true);jdbc=new JdbcTemplate(source);charts=new DashboardChartQueries(jdbc);
  jdbc.execute("CREATE TEMP TABLE academic_years(id BIGINT,school_id BIGINT,label TEXT,start_date DATE,end_date DATE)");
  jdbc.execute("CREATE TEMP TABLE classes(id BIGINT,school_id BIGINT,academic_year_id BIGINT,name TEXT)");
  jdbc.execute("CREATE TEMP TABLE students(id BIGINT,school_id BIGINT)");
  jdbc.execute("CREATE TEMP TABLE student_enrollments(id BIGINT,student_id BIGINT,class_id BIGINT,academic_year_id BIGINT,status TEXT,enrollment_date DATE)");
  jdbc.execute("CREATE TEMP TABLE invoices(id BIGINT,student_id BIGINT,academic_year_id BIGINT,amount_due NUMERIC,discount_amount NUMERIC,due_date DATE,status TEXT)");
  jdbc.execute("CREATE TEMP TABLE payments(invoice_id BIGINT,amount NUMERIC,payment_date DATE)");
  jdbc.execute("CREATE TEMP TABLE attendances(id BIGINT,student_id BIGINT,class_id BIGINT,attendance_date DATE,status TEXT)");
  jdbc.update("INSERT INTO academic_years VALUES(1,10,'2026','2026-09-01','2027-06-30'),(2,20,'Autre','2026-09-01','2027-06-30')");
  jdbc.update("INSERT INTO classes VALUES(1,10,1,'CP'),(2,10,1,'CE'),(3,20,2,'Autre')");
  jdbc.update("INSERT INTO students VALUES(1,10),(2,10),(3,10),(4,20),(5,10)");
  jdbc.update("INSERT INTO student_enrollments VALUES(1,1,1,1,'ACTIVE','2026-09-01'),(2,2,1,1,'ACTIVE','2026-09-01'),(3,3,1,1,'ACTIVE','2026-10-15'),(4,4,3,2,'ACTIVE','2026-09-01')");
 }
 @AfterEach void close(){source.destroy();}
 @Test void splitsNetFeesWithoutCountingCancelledFuturePaymentsOrForeignSchool(){
  jdbc.update("INSERT INTO invoices VALUES(1,1,1,100,20,'2026-10-01','PENDING'),(2,1,1,50,0,'2026-10-17','PENDING'),(3,2,1,40,0,'2026-10-01','PAID'),(4,2,1,999,0,'2026-10-01','CANCELLED'),(5,5,1,30,0,'2026-10-01','PENDING'),(6,4,2,999,0,'2026-10-01','PENDING')");
  jdbc.update("INSERT INTO payments VALUES(1,20,'2026-10-01'),(1,60,'2026-10-18'),(3,45,'2026-10-01'),(4,999,'2026-10-01')");
  var result=charts.receivables(10L,1L,LocalDate.of(2026,10,17));
  assertEquals(0,new BigDecimal("200").compareTo(result.expected()));
  assertEquals(0,new BigDecimal("60").compareTo(result.received()));
  assertEquals(0,new BigDecimal("90").compareTo(result.overdue()));
  assertEquals(0,new BigDecimal("50").compareTo(result.upcoming()));
  assertEquals(0,new BigDecimal("5").compareTo(result.credit()));
  assertEquals(3,result.classes().size());
  assertEquals(0,result.expected().compareTo(result.received().add(result.overdue()).add(result.upcoming())));
  assertNull(charts.receivables(10L,2L,LocalDate.now()));
 }
 @Test void distinguishesMissingFromAbsentAndDeduplicatesLatestStatus(){
  jdbc.update("INSERT INTO attendances VALUES(1,1,1,'2026-10-12','ABSENT'),(2,1,1,'2026-10-12','LATE'),(3,1,1,'2026-10-13','PRESENT'),(4,2,1,'2026-10-13','EXCUSED'),(5,4,3,'2026-10-12','ABSENT')");
  var result=charts.attendance(10L,1L,LocalDate.of(2026,10,17));
  assertEquals(7,result.weeks().size());
  var last=result.weeks().getLast();
  assertEquals(1,last.present());assertEquals(0,last.absent());assertEquals(1,last.late());assertEquals(1,last.excused());assertEquals(1,last.missing());assertEquals(2,last.observedCalls());
  assertEquals(0,result.weeks().getFirst().observedCalls());
  assertEquals(LocalDate.of(2026,9,1),result.fromDate());
  assertTrue(charts.attendance(10L,1L,LocalDate.of(2026,8,1)).weeks().isEmpty());
  assertNull(charts.attendance(10L,2L,LocalDate.now()));
  assertEquals(8,charts.attendance(10L,1L,LocalDate.of(2027,1,1)).weeks().size());
 }

 @Test void bankIncludesOpeningAndActualTransfersWithoutDuplicatingSalaryBridge(){
  jdbc.execute("CREATE TEMP TABLE academic_year_balances(academic_year_id BIGINT,account TEXT,opening_balance NUMERIC)");
  jdbc.execute("ALTER TABLE payments ADD method TEXT");
  jdbc.execute("CREATE TEMP TABLE expenses(school_id BIGINT,amount NUMERIC,payment_method TEXT,expense_date DATE)");
  jdbc.execute("CREATE TEMP TABLE teachers(id BIGINT,school_id BIGINT)");
  jdbc.execute("CREATE TEMP TABLE teacher_payments(id BIGINT,teacher_id BIGINT,amount NUMERIC,payment_date DATE)");
  jdbc.execute("CREATE TEMP TABLE school_payable_payments(teacher_payment_id BIGINT,school_id BIGINT,method TEXT)");
  jdbc.update("INSERT INTO academic_year_balances VALUES(1,'BANK',100),(1,'CASH',99999),(2,'BANK',99999)");
  jdbc.update("INSERT INTO invoices VALUES(1,1,1,300,0,'2026-10-01','CANCELLED'),(2,4,2,999,0,'2026-10-01','PAID')");
  jdbc.update("INSERT INTO payments VALUES(1,300,'2026-10-01','BANK_TRANSFER'),(1,999,'2026-10-18','BANK_TRANSFER'),(1,999,'2026-08-31','BANK_TRANSFER'),(1,999,'2026-10-01','CARD'),(2,999,'2026-10-01','BANK_TRANSFER')");
  jdbc.update("INSERT INTO expenses VALUES(10,50,'BANK_TRANSFER','2026-10-02'),(10,999,'CASH','2026-10-02'),(20,999,'BANK_TRANSFER','2026-10-02')");
  jdbc.update("INSERT INTO teachers VALUES(1,10)");
  jdbc.update("INSERT INTO teacher_payments VALUES(1,1,20,'2026-10-03')");
  jdbc.update("INSERT INTO school_payable_payments VALUES(1,10,'BANK_TRANSFER'),(1,10,'BANK_TRANSFER')");
  var bank=charts.bankPosition(10L,1L,LocalDate.of(2026,10,17));
  assertEquals(0,new BigDecimal("330").compareTo(bank.balance()));
  assertEquals(0,new BigDecimal("70").compareTo(bank.payments()));
  assertNull(charts.bankPosition(10L,2L,LocalDate.of(2026,10,17)));
  assertNull(charts.bankPosition(10L,1L,LocalDate.of(2026,8,31)));
  jdbc.update("DELETE FROM academic_year_balances");
  bank=charts.bankPosition(10L,1L,LocalDate.of(2026,10,17));
  assertNull(bank.openingBalance());
  assertEquals(0,new BigDecimal("230").compareTo(bank.balance()));
 }
}
