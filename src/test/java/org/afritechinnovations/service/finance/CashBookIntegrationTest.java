package org.afritechinnovations.service.finance;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.afritechinnovations.dto.finance.CashBookDto.*;
import org.afritechinnovations.model.common.School;
import org.afritechinnovations.model.common.StaffModule;
import org.afritechinnovations.model.common.User;
import org.afritechinnovations.repository.common.SchoolRepository;
import org.afritechinnovations.security.SchoolPermissions;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.transaction.support.TransactionTemplate;
import java.math.BigDecimal;
import java.time.*;
import java.util.Optional;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Runs only against a named, disposable local PostgreSQL database. */
@EnabledIfSystemProperty(named="cashBookTestUrl",matches="jdbc:postgresql://127\\.0\\.0\\.1:[0-9]+/cash_book_test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class CashBookIntegrationTest {
    static final Long SCHOOL=99001L, OTHER=99002L, OWNER=99101L;
    JdbcTemplate jdbc;
    TransactionTemplate tx;
    CashBookService service;
    SchoolPermissions permissions;
    @BeforeAll void migrate() {
        var source=new DriverManagerDataSource(System.getProperty("cashBookTestUrl"),"postgres","disposable-test-only");
        jdbc=new JdbcTemplate(source);tx=new TransactionTemplate(new DataSourceTransactionManager(source));
        Flyway.configure().dataSource(source).locations("classpath:db/migration").load().migrate();
        jdbc.update("INSERT INTO users(id,first_name,last_name,password_hash) VALUES(99101,'Owner','A','test'),(99102,'Student','A','test'),(99103,'Teacher','A','test') ON CONFLICT DO NOTHING");
        jdbc.update("INSERT INTO schools(id,name,type,owner_id) VALUES(99001,'Cash School','PRIMAIRE',99101),(99002,'Other School','PRIMAIRE',99101) ON CONFLICT DO NOTHING");
        jdbc.update("INSERT INTO academic_years(id,school_id,label,start_date,end_date,is_current) VALUES(99201,99001,'2026','2026-01-01','2026-12-31',true) ON CONFLICT DO NOTHING");
        jdbc.update("INSERT INTO students(id,user_id,school_id,registration_number) VALUES(99301,99102,99001,'CASH-STUDENT') ON CONFLICT DO NOTHING");
        jdbc.update("INSERT INTO teachers(id,user_id,school_id,employee_number) VALUES(99302,99103,99001,'CASH-TEACHER') ON CONFLICT DO NOTHING");
        jdbc.update("INSERT INTO fee_types(id,school_id,name,amount) VALUES(99401,99001,'Tuition',100000) ON CONFLICT DO NOTHING");
        jdbc.update("INSERT INTO invoices(id,student_id,fee_type_id,academic_year_id,amount_due,due_date) VALUES(99501,99301,99401,99201,100000,'2026-10-01') ON CONFLICT DO NOTHING");
        jdbc.update("INSERT INTO expense_categories(id,school_id,name) VALUES(99402,99001,'Cash expenses') ON CONFLICT DO NOTHING");
    }
    @BeforeEach void reset() {
        jdbc.execute("TRUNCATE cash_book_entries,cash_book_month_closures,school_cash_books RESTART IDENTITY CASCADE");
        jdbc.update("DELETE FROM school_payable_payments WHERE school_id=99001");
        jdbc.update("DELETE FROM school_payables WHERE school_id=99001");
        jdbc.update("DELETE FROM teacher_payments WHERE teacher_id=99302");
        jdbc.update("DELETE FROM expenses WHERE school_id=99001");
        jdbc.update("DELETE FROM payments WHERE invoice_id=99501");
        var schools=mock(SchoolRepository.class);permissions=mock(SchoolPermissions.class);
        when(schools.findById(SCHOOL)).thenReturn(Optional.of(School.builder().id(SCHOOL).owner(User.builder().id(OWNER).build()).build()));
        when(schools.findById(OTHER)).thenReturn(Optional.of(School.builder().id(OTHER).owner(User.builder().id(OWNER).build()).build()));
        service=new CashBookService(jdbc,schools,permissions,new ObjectMapper().registerModule(new JavaTimeModule()));
        service.setClock(Clock.fixed(Instant.parse("2026-10-09T12:00:00Z"),ZoneOffset.UTC));
    }
    void open() {tx.executeWithoutResult(s->service.open(SCHOOL,new Open(LocalDate.of(2026,7,1),new BigDecimal("10000")),OWNER,false));}
    Entry entry(String date,Direction direction,String amount) {return new Entry(UUID.randomUUID(),LocalDate.parse(date),direction,new BigDecimal(amount),"Cash movement","V-1");}
    void add(Entry entry) {tx.executeWithoutResult(s->service.add(SCHOOL,entry,OWNER,false));}
    void close(String month,String counted,boolean finalClosure) {
        tx.executeWithoutResult(s->service.close(SCHOOL,new Close(month,new BigDecimal(counted),"Verified",finalClosure),OWNER,false));
    }
    Overview report(String month) {return service.overview(SCHOOL,month,OWNER,false);}
    void cashPayment(String date,String method,String amount) {
        jdbc.update("INSERT INTO payments(invoice_id,payment_date,method,amount) VALUES(99501,?,?,?)",LocalDate.parse(date),method,new BigDecimal(amount));
    }
    @Test void depositsPaymentsEmptyMonthsAndDecemberCarryRemainContinuous() {
        open();add(entry("2026-07-02",Direction.IN,"5000"));add(entry("2026-07-03",Direction.OUT,"2000"));
        assertEquals(0,new BigDecimal("13000").compareTo(report("2026-07").closingBalance()));
        close("2026-07","13000",false);
        var august=report("2026-08");assertTrue(august.rows().isEmpty());assertEquals(0,new BigDecimal("13000").compareTo(august.openingBalance()));
        close("2026-08","13000",false);close("2026-09","13000",false);
        service.setClock(Clock.fixed(Instant.parse("2027-02-01T12:00:00Z"),ZoneOffset.UTC));
        close("2026-10","13000",false);close("2026-11","13000",false);close("2026-12","13000",false);
        assertEquals(0,new BigDecimal("13000").compareTo(report("2027-01").openingBalance()));
        assertEquals(6,report("2027-01").history().size());
    }
    @Test void automaticallyIncludesOnlyCashAndDoesNotCountPayablesTwice() {
        open();cashPayment("2026-07-02","CASH","5000");cashPayment("2026-07-02","BANK_TRANSFER","9000");
        jdbc.update("INSERT INTO expenses(id,school_id,category_id,expense_date,amount,label,payment_method) VALUES(99601,99001,99402,'2026-07-03',1000,'Salary staff','CASH'),(99602,99001,99402,'2026-07-03',7000,'Bank expense','BANK_TRANSFER')");
        jdbc.update("INSERT INTO school_payables(id,school_id,category_id,source,source_key,period,due_date,amount,label) VALUES(99603,99001,99402,'STAFF_SALARY','staff-test','2026-07-01','2026-07-01',1000,'Staff salary'),(99604,99001,99402,'TEACHER_SALARY','teacher-test','2026-07-01','2026-07-01',2000,'Teacher salary')");
        jdbc.update("INSERT INTO teacher_payments(id,teacher_id,pay_month,payment_date,amount) VALUES(99605,99302,'2026-07-01','2026-07-04',2000),(99606,99302,'2026-07-01','2026-07-04',3000)");
        jdbc.update("INSERT INTO school_payable_payments(school_id,payable_id,request_id,payment_date,amount,method,expense_id,teacher_payment_id) VALUES(99001,99603,?,'2026-07-03',1000,'CASH',99601,NULL),(99001,99604,?,'2026-07-04',2000,'CASH',NULL,99605)",UUID.randomUUID(),UUID.randomUUID());
        var report=report("2026-07");assertEquals(3,report.rows().size());
        assertEquals(0,new BigDecimal("5000").compareTo(report.receipts()));
        assertEquals(0,new BigDecimal("3000").compareTo(report.payments()));
        assertEquals(0,new BigDecimal("12000").compareTo(report.closingBalance()));
        assertTrue(service.overview(OTHER,"2026-07",OWNER,false).rows().isEmpty());
        close("2026-07","12000",false);
        assertThrows(DataIntegrityViolationException.class,()->jdbc.update("UPDATE teacher_payments SET amount=2100 WHERE id=99605"));
        assertThrows(DataIntegrityViolationException.class,()->jdbc.update("UPDATE school_payable_payments SET method='BANK_TRANSFER' WHERE teacher_payment_id=99605"));
        assertThrows(DataIntegrityViolationException.class,()->jdbc.update("UPDATE expenses SET payment_method='BANK_TRANSFER' WHERE id=99601"));
    }
    @Test void closureLocksBothSourceDatesAndArchivesTheVisibleRows() {
        open();cashPayment("2026-07-02","CASH","5000");close("2026-07","15000",false);
        assertThrows(DataIntegrityViolationException.class,()->cashPayment("2026-07-03","CASH","100"));
        assertThrows(DataIntegrityViolationException.class,()->jdbc.update("UPDATE payments SET payment_date='2026-08-01' WHERE invoice_id=99501"));
        assertThrows(DataIntegrityViolationException.class,()->jdbc.update("DELETE FROM payments WHERE invoice_id=99501"));
        cashPayment("2026-08-02","CASH","100");
        assertThrows(DataIntegrityViolationException.class,()->jdbc.update("UPDATE payments SET payment_date='2026-07-04' WHERE payment_date='2026-08-02' AND invoice_id=99501"));
        cashPayment("2026-07-03","BANK_TRANSFER","100");
        assertThrows(DataIntegrityViolationException.class,()->jdbc.update("UPDATE payments SET method='CASH' WHERE method='BANK_TRANSFER' AND invoice_id=99501"));
        jdbc.update("UPDATE users SET first_name='Changed' WHERE id=99102");
        assertTrue(report("2026-07").rows().getFirst().label().contains("Student"));
        assertEquals(0,new BigDecimal("15000").compareTo(report("2026-08").openingBalance()));
        jdbc.update("UPDATE users SET first_name='Student' WHERE id=99102");
    }
    @Test void duplicateSubmissionIsIdempotentAndCannotBeRepurposed() {
        open();Entry first=entry("2026-07-02",Direction.IN,"5000");add(first);add(first);
        assertEquals(1,report("2026-07").rows().size());
        assertThrows(IllegalArgumentException.class,()->add(new Entry(first.requestId(),first.date(),first.direction(),new BigDecimal("5100"),first.label(),first.reference())));
        close("2026-07","15000",false);add(first);assertEquals(1,report("2026-07").rows().size());
        assertThrows(IllegalArgumentException.class,()->tx.executeWithoutResult(s->service.delete(SCHOOL,report("2026-07").rows().getFirst().id(),OWNER,false)));
    }
    @Test void closuresMustBeSequentialBalancedAndAfterMonthEnd() {
        open();
        assertThrows(IllegalArgumentException.class,()->close("2026-08","10000",false));
        assertThrows(IllegalArgumentException.class,()->close("2026-07","9999",false));
        close("2026-07","10000",false);close("2026-08","10000",false);close("2026-09","10000",false);
        assertThrows(IllegalArgumentException.class,()->close("2026-10","10000",false));
        close("2026-10","10000",true);
        assertEquals(LocalDate.of(2026,10,9),report("2026-10").book().closedOn());
        assertEquals("2026-10",report("2026-12").month());
        assertThrows(IllegalArgumentException.class,()->add(entry("2026-10-09",Direction.IN,"1")));
    }
    @Test void finalClosureCannotDiscardLaterMovementsAndDatesAreValidated() {
        open();add(entry("2026-08-02",Direction.IN,"100"));
        assertThrows(IllegalArgumentException.class,()->close("2026-07","10000",true));
        assertThrows(IllegalArgumentException.class,()->add(entry("2026-06-30",Direction.IN,"100")));
        assertThrows(IllegalArgumentException.class,()->add(entry("2026-10-10",Direction.IN,"100")));
    }
    @Test void schoolIsolationAndDelegatedPermissionsAreEnforced() {
        assertThrows(AccessDeniedException.class,()->service.overview(SCHOOL,"2026-07",999L,false));
        when(permissions.staffAllows(SCHOOL,999L,StaffModule.EXPENSES)).thenReturn(true);
        assertDoesNotThrow(()->service.overview(SCHOOL,"2026-07",999L,false));
        assertThrows(AccessDeniedException.class,()->service.overview(OTHER,"2026-07",999L,false));
    }
    @Test void closingBalanceCannotHideAnEarlierNegativeCashBalance() {
        open();add(entry("2026-07-02",Direction.OUT,"15000"));add(entry("2026-07-03",Direction.IN,"20000"));
        assertEquals(0,new BigDecimal("15000").compareTo(report("2026-07").closingBalance()));
        assertThrows(IllegalArgumentException.class,()->close("2026-07","15000",false));
        add(entry("2026-07-01",Direction.IN,"5000"));close("2026-07","20000",false);
    }
}
