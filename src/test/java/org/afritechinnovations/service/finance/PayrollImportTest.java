package org.afritechinnovations.service.finance;

import org.afritechinnovations.dto.finance.*;
import org.afritechinnovations.dto.people.TeacherWorkDto;
import org.afritechinnovations.model.common.*;
import org.afritechinnovations.model.finance.PaymentMethod;
import org.afritechinnovations.model.people.Teacher;
import org.afritechinnovations.repository.common.SchoolRepository;
import org.afritechinnovations.repository.people.TeacherRepository;
import org.afritechinnovations.security.SchoolPermissions;
import org.afritechinnovations.service.people.TeacherWorkService;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.AccessDeniedException;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness=Strictness.LENIENT)
class PayrollImportTest {
    @Mock SchoolRepository schools;
    @Mock TeacherRepository teachers;
    @Mock SchoolPermissions permissions;
    @Mock TeacherWorkService teacherWork;
    @Mock SchoolPayableService payables;
    @Mock JdbcTemplate jdbc;
    @InjectMocks PayrollService service;
    Teacher teacher;
    BigDecimal alreadyPaid;
    final String month = "2026-09";
    @BeforeEach void setup() {
        service.setClock(Clock.fixed(Instant.parse("2026-10-17T12:00:00Z"),ZoneOffset.UTC));
        School school=School.builder().id(1L).status(SchoolStatus.ACTIVE).owner(User.builder().id(10L).build()).build();
        teacher=Teacher.builder().id(2L).school(school).user(User.builder().id(3L).active(true).build()).build();
        when(schools.findById(1L)).thenReturn(Optional.of(school));
        when(teachers.findByIdForUpdate(2L)).thenReturn(Optional.of(teacher));
        when(teacherWork.getTeacherDetail(2L,month,10L,true)).thenReturn(
                new TeacherWorkDto.TeacherDetail(null,"FIXED_MONTHLY",new BigDecimal("100000"),List.of(),null));
        when(payables.prepareImportedTeacher(1L,2L,month,10L)).thenReturn(4L);
        alreadyPaid=new BigDecimal("20000");
        when(payables.salary(1L,4L)).thenAnswer(i->row());
        when(payables.paySalary(eq(1L),eq(4L),any(),eq(10L))).thenAnswer(i->{
            PayableDto.Pay payment=i.getArgument(2); alreadyPaid=alreadyPaid.add(payment.amount()); return row();
        });
    }
    PayableDto.Row row() { return new PayableDto.Row(4L,"TEACHER_SALARY",month,LocalDate.of(2026,9,30),"Salaire",null,
            1L,"Paie",new BigDecimal("90000"),alreadyPaid,new BigDecimal("90000").subtract(alreadyPaid),"PARTIAL",false,null); }
    ImportTeacherSalaryRequest request(String paid) { return new ImportTeacherSalaryRequest(month,new BigDecimal("100000"),
            paid==null?null:new BigDecimal(paid),LocalDate.of(2026,9,30),PaymentMethod.BANK_TRANSFER); }
    @Test void cumulativeImportUsesAccountingBridgeAndCanBeRetried() {
        service.importTeacherSalary(1L,2L,request("30000"),10L,false);
        service.importTeacherSalary(1L,2L,request("30000"),10L,false);
        var captor=ArgumentCaptor.forClass(PayableDto.Pay.class);
        verify(payables,times(1)).paySalary(eq(1L),eq(4L),captor.capture(),eq(10L));
        assertEquals(0,new BigDecimal("10000").compareTo(captor.getValue().amount()));
        verify(teacherWork,never()).addRate(any(),any(),any(),anyBoolean());
    }
    @Test void honorsNetSalaryAfterTaxAndRejectsOverpayment() {
        assertThrows(IllegalArgumentException.class,()->service.importTeacherSalary(1L,2L,request("95000"),10L,false));
        verify(payables,never()).paySalary(any(),any(),any(),any());
    }
    @Test void doesNotDeleteExistingPayments() {
        assertThrows(IllegalArgumentException.class,()->service.importTeacherSalary(1L,2L,request("10000"),10L,false));
        service.importTeacherSalary(1L,2L,request(null),10L,false);
        verify(payables,never()).paySalary(any(),any(),any(),any());
    }
    @Test void refusesHourlyContractAndOtherSchool() {
        when(teacherWork.getTeacherDetail(2L,month,10L,true)).thenReturn(
                new TeacherWorkDto.TeacherDetail(null,"HOURLY",BigDecimal.TEN,List.of(),null));
        assertThrows(IllegalArgumentException.class,()->service.importTeacherSalary(1L,2L,request("0"),10L,false));
        teacher.setSchool(School.builder().id(99L).build());
        assertThrows(AccessDeniedException.class,()->service.importTeacherSalary(1L,2L,request("0"),10L,false));
    }
    @Test void requiresPayrollPermission() {
        assertThrows(AccessDeniedException.class,()->service.importTeacherSalary(1L,2L,request("0"),11L,false));
        verify(payables,never()).prepareImportedTeacher(any(),any(),any(),any());
    }
}
