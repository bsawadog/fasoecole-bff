package org.afritechinnovations.service.finance;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.afritechinnovations.dto.finance.PayrollCalculationDto.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;

@Service @RequiredArgsConstructor @Transactional
public class AutomaticPayrollTaxService {
 private final JdbcTemplate jdbc;private final ObjectMapper mapper;private final FiscalObligationSync fiscal;
 public void calculate(Long school,LocalDate period,Long actor){
  if(period.isBefore(LocalDate.of(2026,1,1))||period.isAfter(LocalDate.now().withDayOfMonth(1)))return;
  jdbc.queryForList("SELECT pg_advisory_xact_lock(hashtextextended(?,0))","school-taxes:"+school);
  var rows=jdbc.queryForList("""
   SELECT p.*,coalesce(t.user_id,s.user_id) employee FROM school_payables p
   LEFT JOIN teachers t ON t.id=p.teacher_id AND t.school_id=p.school_id
   LEFT JOIN school_staff s ON p.source_key='staff:'||s.id AND s.school_id=p.school_id
   WHERE p.school_id=? AND p.period=? AND p.source IN ('STAFF_SALARY','TEACHER_SALARY') AND NOT p.cancelled ORDER BY p.id FOR UPDATE OF p
   """,school,period);
  Map<Long,List<Map<String,Object>>> groups=new LinkedHashMap<>();
  for(var row:rows)if(row.get("employee")!=null)groups.computeIfAbsent(((Number)row.get("employee")).longValue(),k->new ArrayList<>()).add(row);
  for(var group:groups.entrySet()){
   var contracts=group.getValue();Map<Long,BigDecimal> weights=new TreeMap<>();contracts.forEach(r->weights.put(((Number)r.get("id")).longValue(),(BigDecimal)r.get("amount")));
   BigDecimal gross=weights.values().stream().reduce(BigDecimal.ZERO,BigDecimal::add);if(gross.signum()==0)continue;
   // An explicit manual override remains authoritative until replaced by a calculation.
   if(contracts.stream().anyMatch(r->r.get("payroll_calculation")==null&&((Number)r.get("payroll_tax_version")).longValue()>0))continue;
   try{
    Input input=null;
    var snapshots=jdbc.queryForList("SELECT input::text FROM school_payroll_calculation_events WHERE school_id=? AND employee_user_id=? AND period<=? ORDER BY period DESC,id DESC LIMIT 1",String.class,school,group.getKey(),period);
    if(!snapshots.isEmpty())input=mapper.readValue(snapshots.getFirst(),Input.class);
    if(input==null){
     BigDecimal z=BigDecimal.ZERO;input=new Input(Regime.SALARIED,gross,z,z,z,z,z,z,z,z,false,0,false,false,"Calcul automatique : régime salarié, brut assimilé au salaire de base, sans charge familiale ni exonération renseignée. Paramètres modifiables dans Salaires.");
    }
    var result=BurkinaPayrollCalculator.calculate(YearMonth.from(period),gross,input);
    var iuts=PayrollService.allocate(result.iuts(),weights);var tpa=PayrollService.allocate(result.tpa(),weights);
    Map<Long,BigDecimal> netWeights=new TreeMap<>();weights.forEach((id,amount)->netWeights.put(id,amount.subtract(iuts.get(id))));
    var deductions=PayrollService.allocate(result.deductions(),netWeights);var vacation=PayrollService.allocate(result.vacationWithholding(),deductions);
    String snapshot=mapper.writeValueAsString(result);
    for(var row:contracts){Long id=((Number)row.get("id")).longValue();
     if(((BigDecimal)row.get("payroll_iuts")).compareTo(iuts.get(id))==0&&((BigDecimal)row.get("payroll_tpa")).compareTo(tpa.get(id))==0&&((BigDecimal)row.get("payroll_deductions")).compareTo(deductions.get(id))==0&&row.get("payroll_calculated_gross")!=null&&((BigDecimal)row.get("payroll_calculated_gross")).compareTo(weights.get(id))==0)continue;
     jdbc.update("UPDATE school_payables SET payroll_iuts=?,payroll_tpa=?,payroll_deductions=?,payroll_vacation_tax=?,payroll_calculation=?::jsonb,payroll_calculated_gross=?,payroll_tax_version=payroll_tax_version+1 WHERE id=? AND school_id=?",iuts.get(id),tpa.get(id),deductions.get(id),vacation.get(id),snapshot,weights.get(id),id,school);
     jdbc.update("INSERT INTO school_payroll_tax_events(school_id,payable_id,iuts,tpa,version,reason,created_by) SELECT school_id,id,payroll_iuts,payroll_tpa,payroll_tax_version,?,? FROM school_payables WHERE id=?",input.reason(),actor,id);
    }
   }catch(com.fasterxml.jackson.core.JsonProcessingException e){throw new IllegalStateException("Paramètres fiscaux de paie illisibles.",e);}
  }
  fiscal.payroll(school,period.getYear(),actor);
 }
 public void existing(Long school,int year,Long actor){
  for(LocalDate month:jdbc.query("SELECT DISTINCT period FROM school_payables WHERE school_id=? AND period BETWEEN ? AND ? AND source IN ('STAFF_SALARY','TEACHER_SALARY') AND NOT cancelled ORDER BY period",(r,n)->r.getDate(1).toLocalDate(),school,LocalDate.of(year,1,1),LocalDate.of(year,12,31)))calculate(school,month,actor);
 }
}
