package org.afritechinnovations.service.finance;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.afritechinnovations.dto.finance.OwnerExpenseDto.*;
import org.afritechinnovations.dto.finance.SchoolTaxDto.Code;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
@EnabledIfSystemProperty(named="expenseFiscalTestUrl",matches="jdbc:postgresql://127\\.0\\.0\\.1:[0-9]+/expense_fiscal_test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ExpenseFiscalIntegrationTest {
 JdbcTemplate jdbc;TransactionTemplate tx;FiscalObligationSync fiscal;AutomaticPayrollTaxService automatic;
 final LocalDate month=LocalDate.now().withDayOfMonth(1);
 @BeforeAll void migrate(){
 var source=new DriverManagerDataSource(System.getProperty("expenseFiscalTestUrl"),"postgres","disposable-test-only");jdbc=new JdbcTemplate(source);tx=new TransactionTemplate(new DataSourceTransactionManager(source));
 Flyway.configure().dataSource(source).locations("classpath:db/migration").load().migrate();
        jdbc.update("INSERT INTO users(id,first_name,last_name,password_hash) VALUES(99101,'Owner','A','test'),(99102,'Student','A','test'),(99103,'Teacher','A','test') ON CONFLICT DO NOTHING");
        jdbc.update("INSERT INTO schools(id,name,type,owner_id) VALUES(99001,'Cash School','PRIMAIRE',99101),(99002,'Other School','PRIMAIRE',99101) ON CONFLICT DO NOTHING");
        jdbc.update("INSERT INTO academic_years(id,school_id,label,start_date,end_date,is_current) VALUES(99201,99001,'2026','2026-01-01','2026-12-31',true) ON CONFLICT DO NOTHING");
        jdbc.update("INSERT INTO students(id,user_id,school_id,registration_number,first_name,last_name) VALUES(99301,99102,99001,'CASH-STUDENT','Student','A') ON CONFLICT DO NOTHING");
        jdbc.update("INSERT INTO teachers(id,user_id,school_id,employee_number) VALUES(99302,99103,99001,'CASH-TEACHER') ON CONFLICT DO NOTHING");
        jdbc.update("INSERT INTO fee_types(id,school_id,name,amount) VALUES(99401,99001,'Tuition',100000) ON CONFLICT DO NOTHING");
        jdbc.update("INSERT INTO invoices(id,student_id,fee_type_id,academic_year_id,amount_due,due_date) VALUES(99501,99301,99401,99201,100000,'2026-10-01') ON CONFLICT DO NOTHING");
        jdbc.update("INSERT INTO expense_categories(id,school_id,name) VALUES(99402,99001,'Cash expenses') ON CONFLICT DO NOTHING");
    }


 @BeforeEach void reset(){
 jdbc.execute("TRUNCATE school_payables,school_payroll_calculation_events,expenses RESTART IDENTITY CASCADE");
 jdbc.update("DELETE FROM school_staff WHERE school_id=99001");
 jdbc.update("INSERT INTO school_staff(id,school_id,user_id,job_title,monthly_salary) VALUES(70001,99001,99103,'Employee',100000)");
 fiscal=new FiscalObligationSync(jdbc);automatic=new AutomaticPayrollTaxService(jdbc,new ObjectMapper().findAndRegisterModules(),fiscal);
 }
 void salary(long id,String source,String key,Long teacher,String gross){jdbc.update("INSERT INTO school_payables(id,school_id,category_id,source,source_key,period,due_date,amount,label,teacher_id) VALUES(?,99001,99402,?,?,?,?,?,'Salary',?)",id,source,key,month,month,new BigDecimal(gross),teacher);}
 void calculate(){tx.executeWithoutResult(s->automatic.calculate(99001L,month,99101L));}
 void amount(String value,String sql){assertEquals(0,new BigDecimal(value).compareTo(jdbc.queryForObject(sql,BigDecimal.class)));}
 @Test void preparesDefaultIutsTpaAndCreatesOnlyOneObligationPerMonth(){
 salary(80001,"STAFF_SALARY","staff:70001",null,"100000");calculate();calculate();
 amount("5895","SELECT payroll_iuts FROM school_payables WHERE id=80001");amount("3000","SELECT payroll_tpa FROM school_payables WHERE id=80001");
 assertEquals(2,jdbc.queryForObject("SELECT count(*) FROM school_tax_obligations WHERE origin='PAYROLL'",Integer.class));
 amount("8895","SELECT sum(amount) FROM school_payables WHERE source='TAX'");
 assertEquals(1,jdbc.queryForObject("SELECT payroll_tax_version FROM school_payables WHERE id=80001",Integer.class));
 }
 @Test void combinesTeacherAndStaffContractsOfSameEmployeeBeforeProgressiveTax(){
 salary(80001,"STAFF_SALARY","staff:70001",null,"100000");salary(80002,"TEACHER_SALARY","teacher:99302",99302L,"100000");calculate();
 amount("18390","SELECT sum(payroll_iuts) FROM school_payables WHERE source LIKE '%SALARY'");amount("6000","SELECT sum(payroll_tpa) FROM school_payables WHERE source LIKE '%SALARY'");
 }
 @Test void lowSalaryProducesZeroTaxObligationInsteadOfMissingObligation(){
 salary(80001,"STAFF_SALARY","staff:70001",null,"20000");calculate();
 amount("0","SELECT p.amount FROM school_payables p JOIN school_tax_obligations t ON t.payable_id=p.id WHERE t.code='IUTS'");
 amount("600","SELECT p.amount FROM school_payables p JOIN school_tax_obligations t ON t.payable_id=p.id WHERE t.code='TPA'");
 }
 Invoice invoice(){return new Invoice("F-001",month,"IFU-TEST",new BigDecimal("100000"),new BigDecimal("118000"),LocalDate.now().plusDays(10),List.of(new InvoiceTax(Code.TVA,"TVA facturée",TaxTreatment.INCLUDED,new BigDecimal("18000")),new InvoiceTax(Code.TVA,"Retenue TVA",TaxTreatment.WITHHELD,new BigDecimal("5400"))));}
 @Test void invoiceKeepsTtcDistinctFromNetSupplierAndDoesNotCreateTaxUntilExplicitAction(){
 var invoice=invoice();ExpenseInvoiceAmounts.validate(invoice,new BigDecimal("112600"),LocalDate.now());
 assertEquals(invoice,ExpenseInvoiceAmounts.read(ExpenseInvoiceAmounts.json(invoice)));
 assertThrows(IllegalArgumentException.class,()->ExpenseInvoiceAmounts.validate(invoice,new BigDecimal("118000"),LocalDate.now()));
 jdbc.update("INSERT INTO expenses(id,school_id,category_id,expense_date,amount,label,payment_method,invoice_details) VALUES(90001,99001,99402,?,112600,'Invoice','BANK_TRANSFER',?)",LocalDate.now(),ExpenseInvoiceAmounts.json(invoice));
 assertEquals(0,jdbc.queryForObject("SELECT count(*) FROM school_tax_obligations",Integer.class));
 tx.executeWithoutResult(s->fiscal.invoice(99001L,90001L,invoice,LocalDate.now(),99101L));
 tx.executeWithoutResult(s->fiscal.invoice(99001L,90001L,invoice,LocalDate.now(),99101L));
 amount("5400","SELECT amount FROM school_payables WHERE source='TAX'");
 assertEquals(1,jdbc.queryForObject("SELECT count(*) FROM school_tax_obligations WHERE origin='EXPENSE'",Integer.class));
 assertTrue(fiscal.hasActiveInvoiceObligations(99001L,90001L));
 jdbc.update("UPDATE school_payables SET cancelled=true WHERE source='TAX'");
 tx.executeWithoutResult(status->fiscal.invoice(99001L,90001L,invoice,LocalDate.now(),99101L));
 assertTrue(fiscal.hasActiveInvoiceObligations(99001L,90001L),"Explicit creation must reactivate an unpaid cancelled invoice obligation");
 assertFalse(fiscal.hasActiveInvoiceObligations(99002L,90001L));
 }
}
