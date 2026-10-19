package org.afritechinnovations.service.finance;
import org.afritechinnovations.dto.finance.*;
import org.afritechinnovations.model.common.*;
import org.afritechinnovations.model.finance.*;
import org.afritechinnovations.repository.common.*;
import org.afritechinnovations.repository.finance.*;
import org.afritechinnovations.repository.people.TeacherRepository;
import org.afritechinnovations.security.SchoolPermissions;
import org.afritechinnovations.service.people.TeacherWorkService;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.security.access.AccessDeniedException;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
@EnabledIfSystemProperty(named="payableTaxesTestUrl",matches="jdbc:postgresql://127\\.0\\.0\\.1:[0-9]+/payable_taxes_test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class PayableTaxesIntegrationTest {
 JdbcTemplate jdbc;TransactionTemplate tx;SchoolPayableService service;
 final LocalDate today=LocalDate.now();
 @BeforeAll void migrate(){
 var source=new DriverManagerDataSource(System.getProperty("payableTaxesTestUrl"),"postgres","disposable-test-only");jdbc=new JdbcTemplate(source);tx=new TransactionTemplate(new DataSourceTransactionManager(source));Flyway.configure().dataSource(source).locations("classpath:db/migration").load().migrate();
        jdbc.update("INSERT INTO users(id,first_name,last_name,password_hash) VALUES(99101,'Owner','A','test'),(99102,'Student','A','test'),(99103,'Teacher','A','test') ON CONFLICT DO NOTHING");
        jdbc.update("INSERT INTO schools(id,name,type,owner_id) VALUES(99001,'Cash School','PRIMAIRE',99101),(99002,'Other School','PRIMAIRE',99101) ON CONFLICT DO NOTHING");
        jdbc.update("INSERT INTO academic_years(id,school_id,label,start_date,end_date,is_current) VALUES(99201,99001,'2026','2026-01-01','2026-12-31',true) ON CONFLICT DO NOTHING");
        jdbc.update("INSERT INTO students(id,user_id,school_id,registration_number) VALUES(99301,99102,99001,'CASH-STUDENT') ON CONFLICT DO NOTHING");
        jdbc.update("INSERT INTO teachers(id,user_id,school_id,employee_number) VALUES(99302,99103,99001,'CASH-TEACHER') ON CONFLICT DO NOTHING");
        jdbc.update("INSERT INTO fee_types(id,school_id,name,amount) VALUES(99401,99001,'Tuition',100000) ON CONFLICT DO NOTHING");
        jdbc.update("INSERT INTO invoices(id,student_id,fee_type_id,academic_year_id,amount_due,due_date) VALUES(99501,99301,99401,99201,100000,'2026-10-01') ON CONFLICT DO NOTHING");
        jdbc.update("INSERT INTO expense_categories(id,school_id,name) VALUES(99402,99001,'Cash expenses') ON CONFLICT DO NOTHING");
    }


 @BeforeEach void reset(){
 jdbc.execute("TRUNCATE school_payables,fixed_school_charges,expenses RESTART IDENTITY CASCADE");
 var school=School.builder().id(99001L).owner(User.builder().id(99101L).build()).build();var schools=mock(SchoolRepository.class);when(schools.findById(99001L)).thenReturn(Optional.of(school));
 var category=ExpenseCategory.builder().id(99402L).school(school).name("Supplies").active(true).build();var categories=mock(ExpenseCategoryRepository.class);when(categories.findById(99402L)).thenReturn(Optional.of(category));
 var users=mock(UserRepository.class);when(users.getReferenceById(99101L)).thenReturn(User.builder().id(99101L).build());
 var expenses=mock(ExpenseRepository.class);when(expenses.saveAndFlush(any(Expense.class))).thenAnswer(a->{Expense e=a.getArgument(0);Long id=jdbc.queryForObject("INSERT INTO expenses(school_id,category_id,expense_date,amount,label,payment_method,managed_payment,expense_taxes,created_by) VALUES(?,?,?,?,?,?,true,?,?) RETURNING id",Long.class,e.getSchool().getId(),e.getCategory().getId(),e.getExpenseDate(),e.getAmount(),e.getLabel(),e.getPaymentMethod().name(),e.getExpenseTaxes(),99101L);e.setId(id);return e;});
 var expenseService=mock(OwnerExpenseService.class);when(expenseService.listCategories(99001L,99101L,true)).thenReturn(List.of(new OwnerExpenseDto.CategoryInfo(99402L,"Supplies",null,null,true,0),new OwnerExpenseDto.CategoryInfo(99403L,"Staff",null,"STAFF_PAYROLL",true,0),new OwnerExpenseDto.CategoryInfo(99404L,"Teachers",null,"PAYROLL",true,0)));
 service=new SchoolPayableService(mock(AutomaticPayrollTaxService.class),jdbc,schools,users,mock(SchoolPermissions.class),categories,expenses,mock(TeacherRepository.class),mock(TeacherWorkService.class),expenseService);
 }
 BigDecimal n(String v){return new BigDecimal(v);}
 ExpenseTaxDetails taxes(){return new ExpenseTaxDetails("F-001",today,"IFU",n("100000"),List.of(new ExpenseTaxDetails.Line("TVA",n("18000"))));}
 PayableDto.Row create(){tx.executeWithoutResult(s->service.create(99001L,new PayableDto.Create(99402L,"Supplies","Supplier",n("118000"),today,null,taxes()),99101L,false));return service.overview(99001L,today.toString().substring(0,7),false,99101L,false).rows().getFirst();}
 @Test void savesTaxedPayableWithoutInflatingAmount(){var row=create();assertEquals(0,row.amount().compareTo(n("118000")));assertEquals(taxes(),row.taxDetails());assertEquals(0,jdbc.queryForObject("SELECT count(*) FROM expenses",Integer.class));}
 @Test void partialPaymentsPreserveVatAndHttpRetryCannotDuplicateIt(){var row=create();var first=new PayableDto.Pay(UUID.randomUUID(),n("59000"),today,PaymentMethod.BANK_TRANSFER,"P1");tx.executeWithoutResult(s->service.pay(99001L,row.id(),first,99101L,false));tx.executeWithoutResult(s->service.pay(99001L,row.id(),first,99101L,false));tx.executeWithoutResult(s->service.pay(99001L,row.id(),new PayableDto.Pay(UUID.randomUUID(),n("59000"),today,PaymentMethod.BANK_TRANSFER,"P2"),99101L,false));var all=jdbc.queryForList("SELECT expense_taxes FROM expenses ORDER BY id",String.class);assertEquals(2,all.size());for(var json:all){var details=ExpenseTaxAmounts.read(json);assertEquals(0,details.excludingTax().compareTo(n("50000")));assertEquals(0,details.taxes().getFirst().amount().compareTo(n("9000")));}assertEquals("PAID",service.overview(99001L,today.toString().substring(0,7),false,99101L,false).rows().getFirst().status());}
 @Test void copiesFixedChargeTaxesOnlyIntoNewMonthlyObligations(){tx.executeWithoutResult(s->service.createFixed(99001L,new PayableDto.FixedRequest(99402L,"Monthly charge","Supplier",n("118000"),5,today.toString().substring(0,7),taxes()),99101L,false));tx.executeWithoutResult(s->service.prepare(99001L,today.toString().substring(0,7),99101L,false));var report=service.overview(99001L,today.toString().substring(0,7),false,99101L,false);assertEquals(taxes(),report.rows().getFirst().taxDetails());assertEquals(taxes(),report.fixedCharges().getFirst().taxDetails());}
 @Test void validatesAmountsAndAuthorizationBeforeCreatingAnyPayable(){assertThrows(IllegalArgumentException.class,()->tx.executeWithoutResult(s->service.create(99001L,new PayableDto.Create(99402L,"Invalid",null,n("100000"),today,null,taxes()),99101L,false)));assertThrows(AccessDeniedException.class,()->service.create(99001L,new PayableDto.Create(99402L,"Denied",null,n("118000"),today,null,taxes()),7L,false));assertEquals(0,jdbc.queryForObject("SELECT count(*) FROM school_payables",Integer.class));}

 @Test void supplierVatCountsTheInvoiceOnceBeforeAndAfterPartialPayments(){
   var row=create();assertEquals(n("18000"),SupplierVatReport.read(jdbc,99001L,today.getYear()).getFirst().amount());
   tx.executeWithoutResult(s->service.pay(99001L,row.id(),new PayableDto.Pay(UUID.randomUUID(),n("59000"),today,PaymentMethod.BANK_TRANSFER,"partial"),99101L,false));
   var report=SupplierVatReport.read(jdbc,99001L,today.getYear());assertEquals(1,report.size());assertEquals(n("18000"),report.getFirst().amount());assertEquals("F-001",report.getFirst().invoiceNumber());assertFalse(report.getFirst().estimatedDate());
   assertTrue(SupplierVatReport.read(jdbc,99002L,today.getYear()).isEmpty());assertTrue(SupplierVatReport.read(jdbc,99001L,today.getYear()-1).isEmpty());
 }
 @Test void supplierVatExcludesCancelledInvoices(){var row=create();jdbc.update("UPDATE school_payables SET cancelled=true WHERE id=?",row.id());assertTrue(SupplierVatReport.read(jdbc,99001L,today.getYear()).isEmpty());}
 @Test void supplierVatIncludesDirectInvoicesButExcludesWithholdingsAndUsesInvoiceYear(){
   var invoice=new OwnerExpenseDto.Invoice("DIRECT",today.minusYears(1),"IFU",n("100000"),n("118000"),today,List.of(new OwnerExpenseDto.InvoiceTax(SchoolTaxDto.Code.TVA,"TVA",OwnerExpenseDto.TaxTreatment.INCLUDED,n("18000")),new OwnerExpenseDto.InvoiceTax(SchoolTaxDto.Code.TVA,"Retenue",OwnerExpenseDto.TaxTreatment.WITHHELD,n("2000"))));
   jdbc.update("INSERT INTO expenses(school_id,category_id,expense_date,amount,label,payment_method,managed_payment,invoice_details,created_by) VALUES(99001,99402,?,116000,'Direct invoice','BANK_TRANSFER',false,?,99101)",today,ExpenseInvoiceAmounts.json(invoice));
   assertTrue(SupplierVatReport.read(jdbc,99001L,today.getYear()).isEmpty());var report=SupplierVatReport.read(jdbc,99001L,today.getYear()-1);assertEquals(1,report.size());assertEquals(n("18000"),report.getFirst().amount());
 }
 @Test void vatLabelsDoNotConfuseOtherTaxes(){assertTrue(SupplierVatReport.isVat("T.V.A."));assertTrue(SupplierVatReport.isVat("TVA 18 %"));assertTrue(SupplierVatReport.isVat("Taxe sur la valeur ajoutée"));assertFalse(SupplierVatReport.isVat("TPA"));assertFalse(SupplierVatReport.isVat("Autres taxes"));}
}
