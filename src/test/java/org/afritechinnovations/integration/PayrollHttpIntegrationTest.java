package org.afritechinnovations.integration;

import com.fasterxml.jackson.databind.JsonNode;
import org.afritechinnovations.security.JwtService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import java.time.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("integration-test")
@EnabledIfSystemProperty(named="payrollTestUrl",matches="jdbc:postgresql://127\\.0\\.0\\.1:[0-9]+/payroll_test")
class PayrollHttpIntegrationTest {
    @Autowired TestRestTemplate http;
    @Autowired JdbcTemplate jdbc;
    @Autowired JwtService jwt;
    @Autowired org.afritechinnovations.service.people.TeacherWorkService teacherWork;
    @Autowired org.afritechinnovations.security.SchoolPermissions schoolPermissions;
    @org.springframework.test.context.bean.override.mockito.MockitoBean org.afritechinnovations.service.common.EmailService emailService;
    final String base="/api/owner/payroll/schools/4500";
    final String month=YearMonth.now().toString();
    @DynamicPropertySource static void properties(DynamicPropertyRegistry p) {
        p.add("spring.datasource.url",()->System.getProperty("payrollTestUrl"));
        p.add("spring.datasource.username",()->"postgres");p.add("spring.datasource.password",()->"disposable-test-only");
        p.add("spring.jpa.hibernate.ddl-auto",()->"validate");
        p.add("jwt.secret",()->"AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA");
        p.add("management.health.mail.enabled",()->"false");
    }
    @BeforeEach void fixture() {
        jdbc.update("""
            INSERT INTO users(id,first_name,last_name,email,password_hash,email_verified,active) VALUES
            (4501,'Owner','Payroll','owner.payroll@test.invalid','test',true,true),
            (4502,'Awa','Dual','dual.payroll@test.invalid','test',true,true),
            (4503,'Issa','Hourly','hourly.payroll@test.invalid','test',true,true),
            (4504,'Ali','Guardian','guardian.payroll@test.invalid','test',true,true),
            (4505,'Mariam','Accountant','accountant.payroll@test.invalid','test',true,true),
            (4506,'Other','Owner','other.payroll@test.invalid','test',true,true) ON CONFLICT DO NOTHING
            """);
        jdbc.update("INSERT INTO schools(id,name,type,status,owner_id) VALUES(4500,'Payroll School','PRIMAIRE','ACTIVE',4501),(4510,'Other Payroll','PRIMAIRE','ACTIVE',4506) ON CONFLICT DO NOTHING");
        for (long[] member : List.of(new long[]{4501,4500},new long[]{4506,4510}))
            jdbc.update("INSERT INTO school_users(user_id,school_id,role_id) SELECT ?,?,id FROM roles WHERE name='SCHOOL_ADMIN' ON CONFLICT DO NOTHING",member[0],member[1]);
        jdbc.update("INSERT INTO school_users(user_id,school_id,role_id) SELECT 4505,4500,id FROM roles WHERE name='STAFF' ON CONFLICT DO NOTHING");
        jdbc.update("DELETE FROM school_payroll_documents WHERE school_id=4500");
        jdbc.update("DELETE FROM school_payroll_calculation_events WHERE school_id=4500");
        jdbc.update("DELETE FROM school_tax_events WHERE school_id=4500");
        jdbc.update("DELETE FROM school_tax_obligations WHERE school_id=4500");
        jdbc.update("DELETE FROM school_payable_payments WHERE school_id=4500");
        jdbc.update("DELETE FROM school_payables WHERE school_id=4500");
        jdbc.update("DELETE FROM school_staff WHERE school_id=4500 AND access_only");
        jdbc.update("DELETE FROM school_users WHERE school_id=4500 AND user_id=4503 AND role_id=(SELECT id FROM roles WHERE name='STAFF')");
        jdbc.update("UPDATE users SET first_name='Issa',last_name='Hourly',email='hourly.payroll@test.invalid',email_verified=true WHERE id=4503");
        jdbc.update("DELETE FROM fixed_school_charges WHERE school_id=4500");
        jdbc.update("DELETE FROM expenses WHERE school_id=4500");
        jdbc.update("DELETE FROM teacher_payments WHERE teacher_id IN (4502,4503)");
        jdbc.update("DELETE FROM teacher_rates WHERE teacher_id IN (4502,4503)");
        jdbc.update("INSERT INTO teachers(id,user_id,school_id,employee_number,hire_date,monthly_salary) VALUES(4502,4502,4500,'EMP-4502',?,80000),(4503,4503,4500,'EMP-4503',?,NULL) ON CONFLICT DO NOTHING",YearMonth.now().atDay(1),YearMonth.now().atDay(1));
        jdbc.update("UPDATE teachers SET hire_date=? WHERE school_id=4500",YearMonth.now().atDay(1));
        jdbc.update("INSERT INTO teacher_rates(teacher_id,rate_type,amount,effective_from) VALUES(4502,'FIXED_MONTHLY',80000,?),(4503,'HOURLY',2500,?)",YearMonth.now().atDay(1),YearMonth.now().atDay(1));
        jdbc.update("""
            INSERT INTO school_staff(id,school_id,user_id,job_title,active,monthly_salary,created_at) VALUES
            (4502,4500,4502,'Directeur adjoint',true,30000,?),(4504,4500,4504,'Gardien',true,NULL,?),(4505,4500,4505,'Comptable',true,50000,?)
            ON CONFLICT DO NOTHING
            """,YearMonth.now().atDay(1).atStartOfDay(),YearMonth.now().atDay(1).atStartOfDay(),YearMonth.now().atDay(1).atStartOfDay());
        jdbc.update("DELETE FROM school_staff_modules WHERE staff_id=4505");
        jdbc.update("INSERT INTO school_staff_modules(staff_id,module) VALUES(4505,'PAYROLL')");
        jdbc.update("INSERT INTO subjects(id,school_id,name) VALUES(4502,4500,'Français'),(4503,4500,'Mathématiques') ON CONFLICT DO NOTHING");
        jdbc.update("INSERT INTO teacher_subjects(teacher_id,subject_id) VALUES(4502,4502),(4502,4503) ON CONFLICT DO NOTHING");
    }
    HttpHeaders headers(long id) {
        String email=id==4501?"owner.payroll@test.invalid":id==4505?"accountant.payroll@test.invalid":"other.payroll@test.invalid";
        var headers=new HttpHeaders();headers.setBearerAuth(jwt.generateToken(id,email,List.of(id==4505?"STAFF":"SCHOOL_ADMIN")));headers.setContentType(MediaType.APPLICATION_JSON);return headers;
    }
    ResponseEntity<JsonNode> request(String path,HttpMethod method,Object body,long actor) {return http.exchange(path,method,new HttpEntity<>(body,headers(actor)),JsonNode.class);}
    JsonNode ok(String path,HttpMethod method,Object body,long actor) {
        var result=request(path,method,body,actor);assertEquals(HttpStatus.OK,result.getStatusCode(),()->String.valueOf(result.getBody()));return result.getBody();
    }
    JsonNode overview() {return ok(base+"?month="+month,HttpMethod.GET,null,4501);}
    JsonNode employee(JsonNode overview,long id) {for(var row:overview.path("employees"))if(row.path("userId").asLong()==id)return row;throw new AssertionError("Employee missing");}
    long payable(String key) {return jdbc.queryForObject("SELECT id FROM school_payables WHERE school_id=4500 AND source_key=? AND period=?",Long.class,key,YearMonth.now().atDay(1));}
    Map<String,Object> payment(int amount,UUID key) {return Map.of("requestId",key,"amount",amount,"date",LocalDate.now().toString(),"method","CASH","reference","PAYROLL-TEST");}
    Map<String,Object> document(String kind,long user,UUID key) {return Map.of("kind",kind,"userId",user,"month",month,"requestId",key);}

    @Test void personnelDirectoryIncludesTeachersAndEditsProfilesAndModuleAccessWithoutAddingASalary() {
        String directory="/api/owner/staff/schools/4500/employees";
        var rows=ok(directory,HttpMethod.GET,null,4501);
        assertEquals(4,rows.size());
        var teacherRow=java.util.stream.StreamSupport.stream(rows.spliterator(),false).filter(r->r.path("staff").path("userId").asLong()==4503).findFirst().orElseThrow();
        assertTrue(teacherRow.path("staff").path("id").isNull());assertEquals(4503,teacherRow.path("teacher").path("id").asLong());
        assertEquals(HttpStatus.FORBIDDEN,request(directory,HttpMethod.GET,null,4505).getStatusCode());
        assertEquals(HttpStatus.FORBIDDEN,request(directory,HttpMethod.GET,null,4506).getStatusCode());
        var profile=new HashMap<String,Object>();profile.put("firstName","Issa modifié");profile.put("lastName","Sawadogo");profile.put("email","issa.modified@test.invalid");profile.put("phone","70001122");profile.put("jobTitle","Enseignant");profile.put("monthlySalary",null);profile.put("modules",List.of("PAYROLL","GRADES"));
        var update=new HashMap<String,Object>();update.put("profile",profile);update.put("employeeNumber","EMP-4503");update.put("specialty","Sciences");update.put("hireDate",YearMonth.now().atDay(1).toString());update.put("administrativeEmployment",false);update.put("teacherRate",null);
        long previousVersion=jdbc.queryForObject("SELECT session_version FROM users WHERE id=4503",Long.class);
        var saved=ok(directory+"/4503",HttpMethod.PUT,update,4501);
        assertEquals("Issa modifié",saved.path("staff").path("firstName").asText());assertEquals("Sciences",saved.path("teacher").path("specialty").asText());
        assertFalse(saved.path("staff").path("emailVerified").asBoolean());
        assertEquals(previousVersion+1,jdbc.queryForObject("SELECT session_version FROM users WHERE id=4503",Long.class));
        assertTrue(jdbc.queryForObject("SELECT access_only FROM school_staff WHERE school_id=4500 AND user_id=4503",Boolean.class));
        assertTrue(schoolPermissions.staffAllows(4500L,4503L,org.afritechinnovations.model.common.StaffModule.PAYROLL));
        assertFalse(schoolPermissions.staffAllows(4510L,4503L,org.afritechinnovations.model.common.StaffModule.PAYROLL));
        ok(base+"/prepare?month="+month,HttpMethod.POST,null,4501);
        assertEquals(1,employee(overview(),4503).path("contracts").size());
        assertEquals(4,ok(directory,HttpMethod.GET,null,4501).size());
        profile.put("modules",List.of());ok(directory+"/4503",HttpMethod.PUT,update,4501);
        assertFalse(schoolPermissions.staffAllows(4500L,4503L,org.afritechinnovations.model.common.StaffModule.PAYROLL));
        assertEquals(4,ok(directory,HttpMethod.GET,null,4501).size());
        profile.put("email","owner.payroll@test.invalid");assertEquals(HttpStatus.BAD_REQUEST,request(directory+"/4503",HttpMethod.PUT,update,4501).getStatusCode());
        assertEquals("issa.modified@test.invalid",jdbc.queryForObject("SELECT email FROM users WHERE id=4503",String.class));
        assertEquals(HttpStatus.FORBIDDEN,request(directory+"/4503",HttpMethod.PUT,update,4506).getStatusCode());
    }

    Map<String,Object> automaticInput() {
        var input=new HashMap<String,Object>();
        input.put("regime","SALARIED");input.put("baseSalary",110000);
        for(String name:List.of("housing","functionAllowance","transport","benefitsInKind","pension","otherSocial","employerContributions")) input.put(name,0);
        input.put("otherDeductions",1000);input.put("upperCategory",false);input.put("familyCharges",0);
        input.put("tpaExempt",false);input.put("approvedManagementCenter",false);input.put("reason","Justificatifs vérifiés");return input;
    }
    @Test void automaticCalculationGroupsContractsChangesNetFreezesAfterPaymentAndPreservesDocuments() {
        ok(base+"/prepare?month="+month,HttpMethod.POST,null,4501);
        var input=automaticInput();String url=base+"/employees/4502/calculation";
        var preview=ok(url+"/preview?month="+month,HttpMethod.POST,input,4501);
        assertEquals(110000,preview.path("result").path("gross").asInt());
        assertEquals(6983,preview.path("result").path("iuts").asInt());
        assertEquals(102017,preview.path("result").path("net").asInt());
        assertEquals(3300,preview.path("result").path("tpa").asInt());
        var apply=Map.of("input",input,"requestId",UUID.randomUUID(),"versions",preview.path("versions"),"grossByPayable",preview.path("grossByPayable"));
        assertEquals(HttpStatus.FORBIDDEN,request(url+"/preview?month="+month,HttpMethod.POST,input,4506).getStatusCode());
        ok(url+"?month="+month,HttpMethod.POST,apply,4501);
        ok(url+"?month="+month,HttpMethod.POST,apply,4501);
        assertEquals(1,jdbc.queryForObject("SELECT count(*) FROM school_payroll_calculation_events WHERE school_id=4500",Integer.class));
        var person=employee(overview(),4502);
        // Sum exact decimals, including allocation cents, instead of rounding each contract's net.
        java.math.BigDecimal actualNet=java.math.BigDecimal.ZERO;
        for(var contract:person.path("contracts"))actualNet=actualNet.add(contract.path("net").decimalValue());
        assertEquals(0,new java.math.BigDecimal("102017").compareTo(actualNet));
        assertEquals(0,new java.math.BigDecimal("6983").compareTo(jdbc.queryForObject("SELECT sum(payroll_iuts) FROM school_payables WHERE school_id=4500 AND payroll_calculation IS NOT NULL",java.math.BigDecimal.class)));
        jdbc.update("UPDATE teacher_rates SET amount=90000 WHERE teacher_id=4502");
        assertEquals(HttpStatus.BAD_REQUEST,request(base+"/payments/"+payable("staff:4502"),HttpMethod.POST,payment(1,UUID.randomUUID()),4501).getStatusCode());
        assertEquals(HttpStatus.BAD_REQUEST,request(base+"/documents",HttpMethod.POST,document("PAYSLIP",4502,UUID.randomUUID()),4501).getStatusCode());
        jdbc.update("UPDATE teacher_rates SET amount=80000 WHERE teacher_id=4502");
        var saved=ok(base+"/documents",HttpMethod.POST,document("PAYSLIP",4502,UUID.randomUUID()),4501);
        assertEquals("BF-CGI-2026-v1",saved.path("snapshot").path("employee").path("contracts").get(0).path("calculation").path("ruleVersion").asText());
        assertEquals(0,jdbc.queryForObject("SELECT count(*) FROM expenses WHERE school_id=4500",Integer.class));
        assertEquals(110000,ok(url+"?month="+month,HttpMethod.GET,null,4501).path("baseSalary").asInt());
        long salary=payable("teacher:4502");ok(base+"/payments/"+salary,HttpMethod.POST,payment(1000,UUID.randomUUID()),4501);
        assertEquals(HttpStatus.BAD_REQUEST,request(url+"/preview?month="+month,HttpMethod.POST,input,4501).getStatusCode());
    }
    @Test void staleCalculationPreviewCannotOverwriteAConcurrentChange() {
        ok(base+"/prepare?month="+month,HttpMethod.POST,null,4501);var input=automaticInput();String url=base+"/employees/4502/calculation";
        var preview=ok(url+"/preview?month="+month,HttpMethod.POST,input,4501);
        jdbc.update("UPDATE school_payables SET payroll_tax_version=payroll_tax_version+1 WHERE id=?",payable("staff:4502"));
        var apply=Map.of("input",input,"requestId",UUID.randomUUID(),"versions",preview.path("versions"),"grossByPayable",preview.path("grossByPayable"));
        assertEquals(HttpStatus.BAD_REQUEST,request(url+"?month="+month,HttpMethod.POST,apply,4501).getStatusCode());
        assertEquals(0,jdbc.queryForObject("SELECT count(*) FROM school_payroll_calculation_events WHERE school_id=4500",Integer.class));
    }
    @Test void vacationsAreTrackedSeparatelyAndSalaryPlusTaxPaymentsDoNotDoubleCountTheGross() {
        jdbc.update("UPDATE teacher_rates SET rate_type='FIXED_MONTHLY',amount=80000 WHERE teacher_id=4503");
        ok(base+"/prepare?month="+month,HttpMethod.POST,null,4501);
        var input=automaticInput();input.put("regime","TEACHING_VACATION");input.put("baseSalary",0);input.put("otherDeductions",0);
        String url=base+"/employees/4503/calculation";var preview=ok(url+"/preview?month="+month,HttpMethod.POST,input,4501);
        assertEquals(1600,preview.path("result").path("vacationWithholding").asInt());
        assertEquals(0,preview.path("result").path("iuts").asInt());assertEquals(0,preview.path("result").path("tpa").asInt());
        var apply=Map.of("input",input,"requestId",UUID.randomUUID(),"versions",preview.path("versions"),"grossByPayable",preview.path("grossByPayable"));
        ok(url+"?month="+month,HttpMethod.POST,apply,4501);
        String taxes="/api/owner/taxes/schools/4500";
        assertEquals(1600,ok(taxes+"/payroll-preview?month="+month,HttpMethod.GET,null,4501).path("vacationWithholding").asInt());
        var taxImport=Map.of("month",month,"dueDate",LocalDate.now().toString(),"creditor","DGI","reason","Retenue sur vacation vérifiée");
        ok(taxes+"/payroll-import",HttpMethod.POST,taxImport,4501);ok(taxes+"/payroll-import",HttpMethod.POST,taxImport,4501);
        long taxId=jdbc.queryForObject("SELECT payable_id FROM school_tax_obligations WHERE school_id=4500 AND code='OTHER'",Long.class);
        assertEquals(1,jdbc.queryForObject("SELECT count(*) FROM school_tax_obligations WHERE school_id=4500",Integer.class));
        assertEquals(HttpStatus.BAD_REQUEST,request(base+"/payments/"+payable("teacher:4503"),HttpMethod.POST,payment(80000,UUID.randomUUID()),4501).getStatusCode());
        ok(base+"/payments/"+payable("teacher:4503"),HttpMethod.POST,payment(78400,UUID.randomUUID()),4501);
        ok(taxes+"/"+taxId+"/payments",HttpMethod.POST,payment(1600,UUID.randomUUID()),4501);
        assertEquals(0,new java.math.BigDecimal("80000").compareTo(jdbc.queryForObject("SELECT (SELECT COALESCE(sum(amount),0) FROM expenses WHERE school_id=4500)+(SELECT COALESCE(sum(p.amount),0) FROM teacher_payments p JOIN teachers t ON t.id=p.teacher_id WHERE t.school_id=4500)",java.math.BigDecimal.class)));
    }
    @Test void listsEveryEmployeeOnceAndPreparesOnlySalaryObligations() {
        var before=overview();assertEquals(4,before.path("employees").size());
        assertEquals(2,employee(before,4502).path("contracts").size());
        assertEquals(2,employee(before,4502).path("subjects").size());
        assertEquals("UNCONFIGURED",employee(before,4504).path("status").asText());
        assertEquals("UNPREPARED",employee(before,4502).path("status").asText());
        // A fixed charge must never be generated by a payroll-only preparation.
        ok(base+"/prepare?month="+month,HttpMethod.POST,null,4501);
        long category=jdbc.queryForObject("SELECT id FROM expense_categories WHERE school_id=4500 AND system_code='STAFF_PAYROLL'",Long.class);
        jdbc.update("INSERT INTO fixed_school_charges(school_id,category_id,label,amount,due_day,start_month) VALUES(4500,?,'Rent',1000,1,?)",category,YearMonth.now().atDay(1));
        ok(base+"/prepare?month="+month,HttpMethod.POST,null,4501);
        assertEquals(4,jdbc.queryForObject("SELECT count(*) FROM school_payables WHERE school_id=4500",Integer.class));
        assertEquals(0,jdbc.queryForObject("SELECT count(*) FROM school_payables WHERE school_id=4500 AND source='FIXED'",Integer.class));
        assertEquals(110000,employee(overview(),4502).path("due").asInt());
        assertEquals("NOT_DUE",employee(overview(),4503).path("status").asText());
    }

    @Test void paymentsAndImmutablePayslipsShareTheExistingAccountingLedger() {
        ok(base+"/prepare?month="+month,HttpMethod.POST,null,4501);
        UUID paymentKey=UUID.randomUUID();
        ok(base+"/payments/"+payable("staff:4502"),HttpMethod.POST,payment(10000,paymentKey),4501);
        ok(base+"/payments/"+payable("staff:4502"),HttpMethod.POST,payment(10000,paymentKey),4501);
        ok(base+"/payments/"+payable("teacher:4502"),HttpMethod.POST,payment(40000,UUID.randomUUID()),4501);
        assertEquals(50000,employee(overview(),4502).path("paid").asInt());
        UUID documentKey=UUID.randomUUID();
        var first=ok(base+"/documents",HttpMethod.POST,document("PAYSLIP",4502,documentKey),4501);
        long id=first.path("id").asLong();
        assertEquals(50000,first.path("snapshot").path("employee").path("paid").asInt());
        ok(base+"/payments/"+payable("staff:4502"),HttpMethod.POST,payment(20000,UUID.randomUUID()),4501);
        ok(base+"/payments/"+payable("teacher:4502"),HttpMethod.POST,payment(40000,UUID.randomUUID()),4501);
        assertEquals("PAID",employee(overview(),4502).path("status").asText());
        var old=ok(base+"/documents/"+id,HttpMethod.GET,null,4501);
        assertEquals(50000,old.path("snapshot").path("employee").path("paid").asInt());
        assertEquals(id,ok(base+"/documents",HttpMethod.POST,document("PAYSLIP",4502,documentKey),4501).path("id").asLong());
        var latest=ok(base+"/documents",HttpMethod.POST,document("PAYSLIP",4502,UUID.randomUUID()),4501);
        assertEquals(110000,latest.path("snapshot").path("employee").path("paid").asInt());
        var detail=ok(base+"/employees/4502?month="+month,HttpMethod.GET,null,4501);
        assertEquals("PAID",detail.path("history").get(0).path("status").asText());assertEquals(2,detail.path("documents").size());
        assertEquals(2,jdbc.queryForObject("SELECT count(*) FROM expenses WHERE school_id=4500",Integer.class));
        assertEquals(2,jdbc.queryForObject("SELECT count(*) FROM teacher_payments WHERE teacher_id=4502",Integer.class));
        assertEquals(110000,jdbc.queryForObject("SELECT sum(amount) FROM school_cash_movements WHERE school_id=4500",Integer.class));
        assertTrue(request(base+"/payments/"+payable("teacher:4502"),HttpMethod.POST,payment(1,UUID.randomUUID()),4501).getStatusCode().is4xxClientError());
    }

    @Test void payrollPermissionIsIsolatedAndDocumentsStayWithinTheirSchool() {
        assertEquals(HttpStatus.OK,request(base+"?month="+month,HttpMethod.GET,null,4505).getStatusCode());
        assertEquals(HttpStatus.FORBIDDEN,request("/api/owner/payables/schools/4500/categories",HttpMethod.GET,null,4505).getStatusCode());
        ok(base+"/prepare?month="+month,HttpMethod.POST,null,4505);
        var certificate=ok(base+"/documents",HttpMethod.POST,document("EMPLOYMENT_CERTIFICATE",4502,UUID.randomUUID()),4505);
        var snapshot=certificate.path("snapshot").path("employee");
        assertEquals(2,snapshot.path("contracts").size());assertTrue(snapshot.path("contracts").get(0).path("rate").isNull());
        assertEquals(0,snapshot.path("paid").asInt());assertEquals(2,snapshot.path("subjects").size());
        assertEquals(HttpStatus.FORBIDDEN,request(base+"/documents/"+certificate.path("id").asLong(),HttpMethod.GET,null,4506).getStatusCode());
        assertTrue(request("/api/owner/payroll/schools/4510/documents/"+certificate.path("id").asLong(),HttpMethod.GET,null,4506).getStatusCode().is4xxClientError());
        long category=jdbc.queryForObject("SELECT id FROM expense_categories WHERE school_id=4500 AND system_code='STAFF_PAYROLL'",Long.class);
        long manual=jdbc.queryForObject("INSERT INTO school_payables(school_id,category_id,source,source_key,period,due_date,amount,label) VALUES(4500,?,'MANUAL','not-a-salary',?,?,10,'Not salary') RETURNING id",Long.class,category,YearMonth.now().atDay(1),LocalDate.now());
        assertEquals(HttpStatus.FORBIDDEN,request(base+"/payments/"+manual,HttpMethod.POST,payment(1,UUID.randomUUID()),4505).getStatusCode());
    }

    @Test void unpreparedPayslipsAndFutureEmploymentCertificatesAreRejected() {
        assertTrue(request(base+"/documents",HttpMethod.POST,document("PAYSLIP",4502,UUID.randomUUID()),4501).getStatusCode().is4xxClientError());
        jdbc.update("UPDATE teachers SET hire_date=? WHERE id=4503",YearMonth.now().plusMonths(1).atDay(1));
        assertTrue(request(base+"/documents",HttpMethod.POST,document("EMPLOYMENT_CERTIFICATE",4503,UUID.randomUUID()),4501).getStatusCode().is4xxClientError());
        assertTrue(request(base+"/prepare?month="+YearMonth.now().plusMonths(1),HttpMethod.POST,null,4501).getStatusCode().is4xxClientError());
    }

    @Test void manualIutsReducesEmployeeNetWhileTpaStaysEmployerSideAcrossPaymentRoutes() {
        ok(base+"/prepare?month="+month,HttpMethod.POST,null,4501);
        long staffId=payable("staff:4502"), teacherId=payable("teacher:4502");
        var staffTax=Map.of("iuts",3000,"tpa",600,"version",0,"reason","Manual payroll calculation");
        assertEquals(HttpStatus.OK,request(base+"/payments/"+staffId+"/taxes",HttpMethod.PUT,staffTax,4505).getStatusCode());
        // An identical retry is idempotent, while a stale different edit is rejected.
        assertEquals(HttpStatus.OK,request(base+"/payments/"+staffId+"/taxes",HttpMethod.PUT,staffTax,4505).getStatusCode());
        assertTrue(request(base+"/payments/"+staffId+"/taxes",HttpMethod.PUT,Map.of("iuts",4000,"tpa",600,"version",0,"reason","Stale edit"),4501).getStatusCode().is4xxClientError());
        assertTrue(request(base+"/payments/"+teacherId+"/taxes",HttpMethod.PUT,Map.of("iuts",80001,"tpa",0,"version",0,"reason","Too much"),4501).getStatusCode().is4xxClientError());
        assertTrue(request(base+"/payments/"+teacherId+"/taxes",HttpMethod.PUT,Map.of("iuts",-1,"tpa",0,"version",0,"reason","Invalid"),4501).getStatusCode().is4xxClientError());
        assertEquals(HttpStatus.OK,request(base+"/payments/"+teacherId+"/taxes",HttpMethod.PUT,Map.of("iuts",8000,"tpa",1600,"version",0,"reason","Manual payroll calculation"),4501).getStatusCode());
        var person=employee(overview(),4502);
        assertEquals(110000,person.path("due").asInt());assertEquals(99000,person.path("remaining").asInt());
        assertEquals(72000,teacherWork.getTeacherDetail(4502L,month,4501L,false).month().remaining().intValue());
        var direct=new org.afritechinnovations.dto.people.TeacherPaymentRequest();
        direct.setMonth(month);direct.setDate(LocalDate.now());direct.setAmount(new java.math.BigDecimal("80000"));
        assertThrows(IllegalArgumentException.class,()->teacherWork.addPayment(4502L,direct,4501L,false));
        assertTrue(request("/api/owner/payables/schools/4500/"+staffId+"/payments",HttpMethod.POST,payment(30000,UUID.randomUUID()),4501).getStatusCode().is4xxClientError());
        ok(base+"/payments/"+staffId,HttpMethod.POST,payment(27000,UUID.randomUUID()),4501);
        ok(base+"/payments/"+teacherId,HttpMethod.POST,payment(72000,UUID.randomUUID()),4501);
        assertEquals("PAID",employee(overview(),4502).path("status").asText());
        assertTrue(request(base+"/payments/"+staffId+"/taxes",HttpMethod.PUT,Map.of("iuts",0,"tpa",0,"version",1,"reason","After payment"),4501).getStatusCode().is4xxClientError());
        var doc=ok(base+"/documents",HttpMethod.POST,document("PAYSLIP",4502,UUID.randomUUID()),4501);
        int iuts=0,tpa=0,net=0;
        for(var c:doc.path("snapshot").path("employee").path("contracts")){iuts+=c.path("iuts").asInt();tpa+=c.path("tpa").asInt();net+=c.path("net").asInt();}
        assertEquals(11000,iuts);assertEquals(2200,tpa);assertEquals(99000,net);
        assertEquals(99000,jdbc.queryForObject("SELECT sum(amount) FROM school_cash_movements WHERE school_id=4500",Integer.class));
        assertEquals(2,jdbc.queryForObject("SELECT count(*) FROM school_payroll_tax_events WHERE school_id=4500",Integer.class));
        assertEquals(99000,ok(base+"/employees/4502?month="+month,HttpMethod.GET,null,4501).path("history").get(0).path("net").asInt());
    }

    final String taxBase="/api/owner/taxes/schools/4500";
    Map<String,Object> taxCreate(String code,int amount,LocalDate start,LocalDate end,UUID key) {
        return Map.of("requestId",key,"code",code,"label",code+" declared","periodStart",start.toString(),"periodEnd",end.toString(),
                "amount",amount,"dueDate",LocalDate.now().minusDays(1).toString(),"creditor","DGI","declarationReference","DECL-TEST","notes","Manual test");
    }
    @Test void taxesShareAccountingAndKeepPartialPaymentsWithoutDuplicateRetries() {
        UUID key=UUID.randomUUID();var input=taxCreate("TVA",100000,YearMonth.now().atDay(1),YearMonth.now().atEndOfMonth(),key);
        long id=ok(taxBase,HttpMethod.POST,input,4501).path("id").asLong();
        assertEquals(id,ok(taxBase,HttpMethod.POST,input,4501).path("id").asLong());
        assertTrue(request(taxBase,HttpMethod.POST,taxCreate("TVA",90000,YearMonth.now().atDay(1),YearMonth.now().atEndOfMonth(),key),4501).getStatusCode().is4xxClientError());
        UUID paymentKey=UUID.randomUUID();
        ok(taxBase+"/"+id+"/payments",HttpMethod.POST,payment(40000,paymentKey),4501);
        ok(taxBase+"/"+id+"/payments",HttpMethod.POST,payment(40000,paymentKey),4501);
        var overview=ok(taxBase+"?year="+Year.now().getValue(),HttpMethod.GET,null,4501);
        assertEquals(100000,overview.path("due").asInt());assertEquals(40000,overview.path("paid").asInt());
        assertEquals(60000,overview.path("remaining").asInt());assertEquals(60000,overview.path("overdue").asInt());
        assertEquals("PARTIAL",overview.path("rows").get(0).path("obligation").path("status").asText());
        var detail=ok(taxBase+"/"+id,HttpMethod.GET,null,4501);assertEquals(1,detail.path("payments").size());assertEquals(1,detail.path("events").size());
        assertEquals(1,jdbc.queryForObject("SELECT count(*) FROM expenses WHERE school_id=4500",Integer.class));
        assertEquals(40000,jdbc.queryForObject("SELECT sum(amount) FROM school_cash_movements WHERE school_id=4500",Integer.class));
        assertTrue(request(taxBase+"/"+id+"/payments",HttpMethod.POST,payment(60001,UUID.randomUUID()),4501).getStatusCode().is4xxClientError());
        assertTrue(request(taxBase+"/"+id+"/cancel",HttpMethod.POST,Map.of("version",0,"reason","Cannot erase paid debt"),4501).getStatusCode().is4xxClientError());
    }

    @Test void payrollTaxesImportOnceRefreshBalancesAndRejectOverlappingManualDeclarations() {
        ok(base+"/prepare?month="+month,HttpMethod.POST,null,4501);
        long salary=payable("teacher:4502");
        assertEquals(HttpStatus.OK,request(base+"/payments/"+salary+"/taxes",HttpMethod.PUT,Map.of("iuts",8000,"tpa",1600,"version",0,"reason","Calculated manually"),4501).getStatusCode());
        var preview=ok(taxBase+"/payroll-preview?month="+month,HttpMethod.GET,null,4501);
        assertEquals(8000,preview.path("iuts").asInt());assertEquals(1600,preview.path("tpa").asInt());
        var input=Map.of("month",month,"dueDate",LocalDate.now().toString(),"creditor","DGI","reason","Checked by accountant");
        assertEquals(HttpStatus.OK,request(taxBase+"/payroll-import",HttpMethod.POST,input,4501).getStatusCode());
        assertEquals(HttpStatus.OK,request(taxBase+"/payroll-import",HttpMethod.POST,input,4501).getStatusCode());
        assertEquals(2,jdbc.queryForObject("SELECT count(*) FROM school_tax_obligations WHERE school_id=4500",Integer.class));
        assertEquals(0,jdbc.queryForObject("SELECT count(*) FROM expenses WHERE school_id=4500",Integer.class));
        assertTrue(request(taxBase,HttpMethod.POST,taxCreate("IUTS",8000,YearMonth.now().atDay(1),YearMonth.now().atEndOfMonth(),UUID.randomUUID()),4501).getStatusCode().is4xxClientError());
        long taxId=jdbc.queryForObject("SELECT payable_id FROM school_tax_obligations WHERE school_id=4500 AND code='IUTS'",Long.class);
        ok(taxBase+"/"+taxId+"/payments",HttpMethod.POST,payment(5000,UUID.randomUUID()),4501);
        assertEquals(HttpStatus.OK,request(base+"/payments/"+salary+"/taxes",HttpMethod.PUT,Map.of("iuts",9000,"tpa",1800,"version",1,"reason","Corrected calculation"),4501).getStatusCode());
        assertEquals(HttpStatus.OK,request(taxBase+"/payroll-import",HttpMethod.POST,input,4501).getStatusCode());
        var refreshed=ok(taxBase+"/"+taxId,HttpMethod.GET,null,4501);
        assertEquals(9000,refreshed.path("tax").path("obligation").path("amount").asInt());assertEquals(4000,refreshed.path("tax").path("obligation").path("remaining").asInt());
        assertEquals(1,refreshed.path("payments").size());assertEquals(2,refreshed.path("events").size());
        assertEquals(HttpStatus.OK,request(base+"/payments/"+salary+"/taxes",HttpMethod.PUT,Map.of("iuts",1000,"tpa",100,"version",2,"reason","Below payment"),4501).getStatusCode());
        assertTrue(request(taxBase+"/payroll-import",HttpMethod.POST,input,4501).getStatusCode().is4xxClientError());
        assertEquals(9000,ok(taxBase+"/"+taxId,HttpMethod.GET,null,4501).path("tax").path("obligation").path("amount").asInt());
    }

    @Test void taxPermissionIsSchoolScopedAndOldUnpaidDebtsRemainVisible() {
        jdbc.update("DELETE FROM school_staff_modules WHERE staff_id=4505");jdbc.update("INSERT INTO school_staff_modules(staff_id,module) VALUES(4505,'TAXES')");
        LocalDate start=LocalDate.of(Year.now().getValue()-1,1,1),end=start.withMonth(12).withDayOfMonth(31);
        long id=ok(taxBase,HttpMethod.POST,taxCreate("PATENTE",50000,start,end,UUID.randomUUID()),4505).path("id").asLong();
        assertTrue(ok(taxBase+"?year="+Year.now().getValue(),HttpMethod.GET,null,4505).path("rows").get(0).path("carryover").asBoolean());
        assertEquals(HttpStatus.FORBIDDEN,request(base+"?month="+month,HttpMethod.GET,null,4505).getStatusCode());
        assertEquals(HttpStatus.FORBIDDEN,request(taxBase+"/"+id,HttpMethod.GET,null,4506).getStatusCode());
        assertTrue(request("/api/owner/taxes/schools/4510/"+id,HttpMethod.GET,null,4506).getStatusCode().is4xxClientError());
        assertTrue(request("/api/owner/payables/schools/4500/"+id+"/cancel",HttpMethod.POST,null,4501).getStatusCode().is4xxClientError());
        assertEquals(HttpStatus.OK,request(taxBase+"/"+id+"/cancel",HttpMethod.POST,Map.of("version",0,"reason","Not applicable"),4505).getStatusCode());
        assertEquals(0,ok(taxBase+"?year="+Year.now().getValue(),HttpMethod.GET,null,4505).path("rows").size());
        assertEquals("CANCELLED",ok(taxBase+"/"+id,HttpMethod.GET,null,4505).path("tax").path("obligation").path("status").asText());
        assertTrue(request(taxBase,HttpMethod.POST,taxCreate("TVA",-1,end,start,UUID.randomUUID()),4505).getStatusCode().is4xxClientError());
    }
}
