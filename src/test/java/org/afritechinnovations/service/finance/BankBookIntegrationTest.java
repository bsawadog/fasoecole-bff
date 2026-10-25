package org.afritechinnovations.service.finance;
import org.afritechinnovations.dto.finance.BankBookDto.*;
import org.afritechinnovations.model.common.*;
import org.afritechinnovations.repository.common.SchoolRepository;
import org.afritechinnovations.security.SchoolPermissions;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.transaction.support.TransactionTemplate;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
@EnabledIfSystemProperty(named="bankBookTestUrl",matches="jdbc:postgresql://127\\.0\\.0\\.1:[0-9]+/bank_book_test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class BankBookIntegrationTest {
 static final Long SCHOOL=99001L,OTHER=99002L,OWNER=99101L;
 JdbcTemplate jdbc;TransactionTemplate tx;BankBookService service;SchoolPermissions permissions;
 @BeforeAll void migrate(){
  var source=new DriverManagerDataSource(System.getProperty("bankBookTestUrl"),"postgres","disposable-test-only");
  jdbc=new JdbcTemplate(source);tx=new TransactionTemplate(new DataSourceTransactionManager(source));
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
  jdbc.execute("TRUNCATE bank_book_entries,school_bank_books RESTART IDENTITY CASCADE");
  jdbc.update("DELETE FROM school_payable_payments WHERE school_id=99001");
  jdbc.update("DELETE FROM school_payables WHERE school_id=99001");
  jdbc.update("DELETE FROM teacher_payments WHERE teacher_id=99302");
  jdbc.update("DELETE FROM expenses WHERE school_id=99001");
  jdbc.update("DELETE FROM payments WHERE invoice_id=99501");
  var schools=mock(SchoolRepository.class);permissions=mock(SchoolPermissions.class);
  when(schools.findById(SCHOOL)).thenReturn(Optional.of(School.builder().id(SCHOOL).owner(User.builder().id(OWNER).build()).build()));
  when(schools.findById(OTHER)).thenReturn(Optional.of(School.builder().id(OTHER).owner(User.builder().id(OWNER).build()).build()));
  service=new BankBookService(jdbc,schools,permissions);service.setClock(Clock.fixed(Instant.parse("2026-10-09T12:00:00Z"),ZoneOffset.UTC));
 }
 void open(){tx.executeWithoutResult(s->service.open(SCHOOL,new Open(LocalDate.of(2026,7,1),new BigDecimal("10000")),OWNER,false));}
 Entry entry(String date,Direction direction,String amount){return new Entry(UUID.randomUUID(),LocalDate.parse(date),direction,new BigDecimal(amount),"Bank movement","V-1");}
 void add(Entry entry){tx.executeWithoutResult(s->service.add(SCHOOL,entry,OWNER,false));}
 Overview report(String month){return service.overview(SCHOOL,month,OWNER,false);}
 void amount(String expected,BigDecimal actual){assertEquals(0,new BigDecimal(expected).compareTo(actual));}
 @Test void reportsCarryAcrossEmptyMonthsAndYearChangeAndAllowsOverdraft(){
  open();add(entry("2026-07-02",Direction.IN,"5000"));add(entry("2026-07-03",Direction.OUT,"20000"));
  amount("-5000",report("2026-07").closingBalance());
  var august=report("2026-08");assertTrue(august.rows().isEmpty());amount("-5000",august.openingBalance());
  service.setClock(Clock.fixed(Instant.parse("2027-01-05T12:00:00Z"),ZoneOffset.UTC));
  amount("-5000",report("2027-01").openingBalance());
 }
 @Test void includesTransfersOnceAndExcludesCashCardMobileAndUnknownTeacherMethods(){
  open();
  for(String method:List.of("CASH","BANK_TRANSFER","CARD","MOBILE_MONEY"))jdbc.update("INSERT INTO payments(invoice_id,payment_date,method,amount) VALUES(99501,'2026-07-02',?,5000)",method);
  jdbc.update("INSERT INTO expenses(id,school_id,category_id,expense_date,amount,label,payment_method) VALUES(99601,99001,99402,'2026-07-03',1000,'Staff salary','BANK_TRANSFER'),(99602,99001,99402,'2026-07-03',7000,'Cash expense','CASH')");
  jdbc.update("INSERT INTO school_payables(id,school_id,category_id,source,source_key,period,due_date,amount,label) VALUES(99603,99001,99402,'STAFF_SALARY','staff-test','2026-07-01','2026-07-01',1000,'Staff salary'),(99604,99001,99402,'TEACHER_SALARY','teacher-test','2026-07-01','2026-07-01',2000,'Teacher salary')");
  jdbc.update("INSERT INTO teacher_payments(id,teacher_id,pay_month,payment_date,amount) VALUES(99605,99302,'2026-07-01','2026-07-04',2000),(99606,99302,'2026-07-01','2026-07-04',3000)");
  jdbc.update("INSERT INTO school_payable_payments(school_id,payable_id,request_id,payment_date,amount,method,expense_id,teacher_payment_id) VALUES(99001,99603,?,'2026-07-03',1000,'BANK_TRANSFER',99601,NULL),(99001,99604,?,'2026-07-04',2000,'BANK_TRANSFER',NULL,99605)",UUID.randomUUID(),UUID.randomUUID());
  var report=report("2026-07");assertEquals(3,report.rows().size());amount("5000",report.receipts());amount("3000",report.payments());amount("12000",report.closingBalance());
  assertTrue(service.overview(OTHER,"2026-07",OWNER,false).rows().isEmpty());
 }
 @Test void retriesAreIdempotentButPayloadCannotBeRepurposed(){
  open();var first=entry("2026-07-02",Direction.IN,"5000");add(first);add(first);assertEquals(1,report("2026-07").rows().size());
  assertThrows(IllegalArgumentException.class,()->add(new Entry(first.requestId(),first.date(),Direction.OUT,first.amount(),first.label(),first.reference())));
 }
 @Test void protectsSchoolAndManualDeletionAndChecksDates(){
  open();add(entry("2026-07-02",Direction.IN,"5000"));long id=report("2026-07").rows().getFirst().id();
  tx.executeWithoutResult(s->service.open(OTHER,new Open(LocalDate.of(2026,7,1),BigDecimal.ZERO),OWNER,false));
  assertThrows(IllegalArgumentException.class,()->service.delete(OTHER,id,OWNER,false));
  assertThrows(AccessDeniedException.class,()->service.overview(SCHOOL,"2026-07",7L,false));
  when(permissions.staffAllows(SCHOOL,7L,StaffModule.BANK_BOOK)).thenReturn(true);
  assertEquals(1,service.overview(SCHOOL,"2026-07",7L,false).rows().size());
  assertThrows(IllegalArgumentException.class,()->add(entry("2026-06-30",Direction.IN,"5")));
  assertThrows(IllegalArgumentException.class,()->add(entry("2026-10-10",Direction.IN,"5")));
  tx.executeWithoutResult(s->service.delete(SCHOOL,id,OWNER,false));assertTrue(report("2026-07").rows().isEmpty());
 }
 @Test void openingExcludesEarlierAutomaticEntriesAndPreservesCents(){
  jdbc.update("INSERT INTO payments(invoice_id,payment_date,method,amount) VALUES(99501,'2026-06-30','BANK_TRANSFER',5000),(99501,'2026-07-01','BANK_TRANSFER',10.25)");
  open();amount("10010.25",report("2026-07").closingBalance());
  assertThrows(IllegalArgumentException.class,()->service.open(SCHOOL,new Open(LocalDate.of(2026,7,1),BigDecimal.ZERO),OWNER,false));
  assertThrows(IllegalArgumentException.class,()->BankBookService.money(new BigDecimal("1.001"),true));
  assertThrows(IllegalArgumentException.class,()->BankBookService.money(BigDecimal.ZERO,true));
  amount("-10.50",BankBookService.money(new BigDecimal("-10.5"),false));
 }
}
