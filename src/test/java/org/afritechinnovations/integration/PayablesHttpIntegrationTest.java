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
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("integration-test")
@EnabledIfSystemProperty(named="schoolLifeTestUrl", matches="jdbc:postgresql://127\\.0\\.0\\.1:[0-9]+/school_life_test")
class PayablesHttpIntegrationTest {
    @Autowired TestRestTemplate http;
    @Autowired JdbcTemplate jdbc;
    @Autowired JwtService jwt;
    String base="/api/owner/payables/schools/3500";
    String month=YearMonth.now().toString();
    long category;

    @DynamicPropertySource static void properties(DynamicPropertyRegistry p) {
        p.add("spring.datasource.url",()->System.getProperty("schoolLifeTestUrl"));
        p.add("spring.datasource.username",()->"postgres");
        p.add("spring.datasource.password",()->"disposable-test-only");
        p.add("spring.jpa.hibernate.ddl-auto",()->"validate");
        p.add("jwt.secret",()->"AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA");
        p.add("management.health.mail.enabled",()->"false");
    }
    @BeforeEach void fixture() {
        jdbc.update("INSERT INTO users(id,first_name,last_name,email,password_hash,email_verified,active) VALUES(3501,'Owner','Finance','owner.payables@test.invalid','test',true,true),(3502,'Staff','Finance','staff.payables@test.invalid','test',true,true),(3503,'Teacher','Finance','teacher.payables@test.invalid','test',true,true),(3504,'Outsider','Finance','other.payables@test.invalid','test',true,true) ON CONFLICT DO NOTHING");
        jdbc.update("INSERT INTO schools(id,name,type,owner_id) VALUES(3500,'Finance School','PRIMAIRE',3501),(3510,'Other Finance School','PRIMAIRE',3504) ON CONFLICT DO NOTHING");
        jdbc.update("INSERT INTO school_users(user_id,school_id,role_id) SELECT 3501,3500,id FROM roles WHERE name='SCHOOL_ADMIN' ON CONFLICT DO NOTHING");
        jdbc.update("INSERT INTO school_users(user_id,school_id,role_id) SELECT 3504,3510,id FROM roles WHERE name='SCHOOL_ADMIN' ON CONFLICT DO NOTHING");
        jdbc.update("INSERT INTO school_users(user_id,school_id,role_id) SELECT 3502,3500,id FROM roles WHERE name='STAFF' ON CONFLICT DO NOTHING");
        jdbc.update("DELETE FROM school_payable_payments WHERE school_id=3500");
        jdbc.update("DELETE FROM school_payables WHERE school_id=3500");
        jdbc.update("DELETE FROM fixed_school_charges WHERE school_id=3500");
        jdbc.update("DELETE FROM expenses WHERE school_id=3500");
        jdbc.update("DELETE FROM teacher_payments WHERE teacher_id=3503");
        jdbc.update("DELETE FROM teacher_rates WHERE teacher_id=3503");
        jdbc.update("INSERT INTO teachers(id,user_id,school_id,employee_number,hire_date,monthly_salary) VALUES(3503,3503,3500,'PAY-3503',?,80000) ON CONFLICT DO NOTHING",YearMonth.now().atDay(1));
        jdbc.update("INSERT INTO teacher_rates(teacher_id,rate_type,amount,effective_from) VALUES(3503,'FIXED_MONTHLY',80000,?)",YearMonth.now().atDay(1));
        jdbc.update("INSERT INTO school_staff(id,school_id,user_id,job_title,active,monthly_salary,created_at) VALUES(3502,3500,3502,'Comptable',true,60000,?) ON CONFLICT DO NOTHING",YearMonth.now().atDay(1).atStartOfDay());
        jdbc.update("DELETE FROM school_staff_modules WHERE staff_id=3502");
        var cats = request(base+"/categories",HttpMethod.GET,null,owner());
        assertEquals(HttpStatus.OK,cats.getStatusCode(),()->String.valueOf(cats.getBody()));
        category=cats.getBody().get(1).get("id").asLong();
        // Select a category explicitly allowed for ordinary charges.
        for(var c : cats.getBody()) if(!"PAYROLL".equals(c.path("systemCode").asText())) { category=c.path("id").asLong(); break; }
    }
    HttpHeaders owner() { return headers(3501,"SCHOOL_ADMIN"); }
    HttpHeaders headers(long id,String role) {
        String email=id==3501 ? "owner.payables@test.invalid" : id==3502 ? "staff.payables@test.invalid" : "other.payables@test.invalid";
        var h=new HttpHeaders();h.setBearerAuth(jwt.generateToken(id,email,List.of(role)));h.setContentType(MediaType.APPLICATION_JSON);return h;
    }
    ResponseEntity<JsonNode> request(String path,HttpMethod method,Object body,HttpHeaders headers) {
        return http.exchange(path,method,new HttpEntity<>(body,headers),JsonNode.class);
    }
    JsonNode overview() {
        var response=request(base+"?month="+month,HttpMethod.GET,null,owner());
        assertEquals(HttpStatus.OK,response.getStatusCode(),()->String.valueOf(response.getBody()));return response.getBody();
    }
    void prepare() {
        var result=request(base+"/prepare?month="+month,HttpMethod.POST,Map.of(),owner());
        assertEquals(HttpStatus.OK,result.getStatusCode(),()->String.valueOf(result.getBody()));
    }
    Map<String,Object> payment(BigDecimal amount,UUID id) {
        return Map.of("requestId",id,"amount",amount,"date",LocalDate.now().toString(),"method","CASH","reference","PAYABLES-TEST");
    }
    long manual(BigDecimal amount) {
        var result=request(base,HttpMethod.POST,Map.of("categoryId",category,"label","School purchase","amount",amount,"dueDate",LocalDate.now().toString()),owner());
        assertEquals(HttpStatus.OK,result.getStatusCode(),()->String.valueOf(result.getBody()));
        for(var row : overview().path("rows")) if(row.path("source").asText().equals("MANUAL")) return row.path("id").asLong();
        throw new AssertionError("Manual obligation missing");
    }
    @Test void monthlyPayrollAndFixedChargesArePreparedOnceAndSalariesReachCashExpensesOnce() {
        var fixed=request(base+"/fixed",HttpMethod.POST,Map.of("categoryId",category,"label","Rent","amount",30000,"dueDay",31,"startMonth",month),owner());
        assertEquals(HttpStatus.OK,fixed.getStatusCode());prepare();prepare();
        assertEquals(3,overview().path("rows").size());
        long teacher=0,staff=0;
        for(var row : overview().path("rows")) {
            if(row.path("source").asText().equals("TEACHER_SALARY")) teacher=row.path("id").asLong();
            if(row.path("source").asText().equals("STAFF_SALARY")) staff=row.path("id").asLong();
        }
        var first=request(base+"/"+teacher+"/payments",HttpMethod.POST,payment(new BigDecimal("20000"),UUID.randomUUID()),owner());
        assertEquals(HttpStatus.OK,first.getStatusCode(),()->String.valueOf(first.getBody()));
        assertEquals("PARTIAL",first.getBody().path("status").asText());
        assertEquals(60000,first.getBody().path("remaining").asInt());
        var second=request(base+"/"+staff+"/payments",HttpMethod.POST,payment(new BigDecimal("60000"),UUID.randomUUID()),owner());
        assertEquals(HttpStatus.OK,second.getStatusCode(),()->String.valueOf(second.getBody()));
        assertEquals("PAID",second.getBody().path("status").asText());
        assertEquals(1,jdbc.queryForObject("SELECT count(*) FROM expenses WHERE school_id=3500",Integer.class));
        assertEquals(1,jdbc.queryForObject("SELECT count(*) FROM teacher_payments WHERE teacher_id=3503",Integer.class));
        long teacherPayment=jdbc.queryForObject("SELECT id FROM teacher_payments WHERE teacher_id=3503",Long.class);
        assertTrue(request("/api/teacher-work/teachers/3503/payments/"+teacherPayment,HttpMethod.DELETE,null,owner()).getStatusCode().is4xxClientError());
        var cash=request("/api/owner/expenses/schools/3500/summary",HttpMethod.GET,null,owner());
        assertEquals(HttpStatus.OK,cash.getStatusCode());assertEquals(80000,cash.getBody().path("expenses").asInt());
    }
    @Test void partialPaymentsAreIdempotentAndCannotExceedBalanceOrBeDeletedInCashLedger() {
        long id=manual(new BigDecimal("100000"));UUID key=UUID.randomUUID();
        var payload=payment(new BigDecimal("40000"),key);
        var first=request(base+"/"+id+"/payments",HttpMethod.POST,payload,owner());
        assertEquals(HttpStatus.OK,first.getStatusCode());assertEquals(60000,first.getBody().path("remaining").asInt());
        assertEquals(HttpStatus.OK,request(base+"/"+id+"/payments",HttpMethod.POST,payload,owner()).getStatusCode());
        assertEquals(1,jdbc.queryForObject("SELECT count(*) FROM expenses WHERE school_id=3500",Integer.class));
        assertTrue(request(base+"/"+id+"/payments",HttpMethod.POST,payment(new BigDecimal("60000.01"),UUID.randomUUID()),owner()).getStatusCode().is4xxClientError());
        long expense=jdbc.queryForObject("SELECT id FROM expenses WHERE school_id=3500",Long.class);
        assertTrue(request("/api/owner/expenses/expenses/"+expense,HttpMethod.DELETE,null,owner()).getStatusCode().is4xxClientError());
        assertTrue(request(base+"/"+id+"/cancel",HttpMethod.POST,Map.of(),owner()).getStatusCode().is4xxClientError());
        var settled=request(base+"/"+id+"/payments",HttpMethod.POST,payment(new BigDecimal("60000"),UUID.randomUUID()),owner());
        assertEquals(HttpStatus.OK,settled.getStatusCode());assertEquals("PAID",settled.getBody().path("status").asText());
        assertEquals(2,request(base+"/"+id+"/payments",HttpMethod.GET,null,owner()).getBody().size());
    }
    @Test void concurrentPaymentsCannotPayTheSameBalanceTwice() {
        long id=manual(new BigDecimal("100"));
        var a=CompletableFuture.supplyAsync(() -> request(base+"/"+id+"/payments",HttpMethod.POST,payment(new BigDecimal("70"),UUID.randomUUID()),owner()));
        var b=CompletableFuture.supplyAsync(() -> request(base+"/"+id+"/payments",HttpMethod.POST,payment(new BigDecimal("70"),UUID.randomUUID()),owner()));
        var responses=List.of(a.join(),b.join());
        assertEquals(1,responses.stream().filter(r -> r.getStatusCode().is2xxSuccessful()).count());
        assertEquals(1,responses.stream().filter(r -> r.getStatusCode().is4xxClientError()).count());
        assertEquals(1,jdbc.queryForObject("SELECT count(*) FROM school_payable_payments WHERE payable_id=?",Integer.class,id));
    }
    @Test void outsidersCannotReadOrPayAndUnpaidCancellationPreservesHistoryWithoutCashOutflow() {
        long id=manual(new BigDecimal("50000"));
        var outsider=headers(3504,"SCHOOL_ADMIN");
        assertEquals(HttpStatus.FORBIDDEN,request(base+"?month="+month,HttpMethod.GET,null,outsider).getStatusCode());
        assertEquals(HttpStatus.FORBIDDEN,request(base+"/"+id+"/payments",HttpMethod.POST,payment(BigDecimal.ONE,UUID.randomUUID()),outsider).getStatusCode());
        assertEquals(HttpStatus.OK,request(base+"/"+id+"/cancel",HttpMethod.POST,Map.of(),owner()).getStatusCode());
        assertEquals("CANCELLED",overview().path("rows").get(0).path("status").asText());
        assertEquals(0,overview().path("remaining").asInt());
        assertEquals(0,jdbc.queryForObject("SELECT count(*) FROM expenses WHERE school_id=3500",Integer.class));
    }
    @Test void financeDelegationAllowsScopedPaymentsAndCanBeRevoked() {
        long id=manual(new BigDecimal("100"));var staff=headers(3502,"STAFF");
        assertEquals(HttpStatus.FORBIDDEN,request(base+"?month="+month,HttpMethod.GET,null,staff).getStatusCode());
        jdbc.update("INSERT INTO school_staff_modules(staff_id,module) VALUES(3502,'FINANCE')");
        assertEquals(HttpStatus.OK,request(base+"?month="+month,HttpMethod.GET,null,staff).getStatusCode());
        assertEquals(HttpStatus.OK,request(base+"/"+id+"/payments",HttpMethod.POST,payment(new BigDecimal("20"),UUID.randomUUID()),staff).getStatusCode());
        assertEquals(HttpStatus.FORBIDDEN,request("/api/owner/payables/schools/3510?month="+month,HttpMethod.GET,null,staff).getStatusCode());
        jdbc.update("DELETE FROM school_staff_modules WHERE staff_id=3502");
        assertEquals(HttpStatus.FORBIDDEN,request(base+"/"+id+"/payments",HttpMethod.POST,payment(BigDecimal.ONE,UUID.randomUUID()),staff).getStatusCode());
    }
    @Test void earlierUnpaidExpensesRemainVisibleUntilSettled() {
        YearMonth earlier=YearMonth.now().minusMonths(1);
        var result=request(base,HttpMethod.POST,Map.of("categoryId",category,"label","Earlier rent","amount",1000,"dueDate",earlier.atEndOfMonth().toString()),owner());
        assertEquals(HttpStatus.OK,result.getStatusCode());
        var rows=overview().path("rows");assertEquals(1,rows.size());
        assertTrue(rows.get(0).path("overdue").asBoolean());assertEquals(earlier.toString(),rows.get(0).path("period").asText());
        long id=rows.get(0).path("id").asLong();
        assertEquals(HttpStatus.OK,request(base+"/"+id+"/payments",HttpMethod.POST,payment(new BigDecimal("1000"),UUID.randomUUID()),owner()).getStatusCode());
        assertEquals(0,overview().path("rows").size());
        var earlierRows=request(base+"?month="+earlier,HttpMethod.GET,null,owner()).getBody().path("rows");
        assertEquals("PAID",earlierRows.get(0).path("status").asText());
    }
}
