package org.afritechinnovations.service.finance;
import lombok.RequiredArgsConstructor;
import org.afritechinnovations.dto.finance.OwnerExpenseDto.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;

@Service @RequiredArgsConstructor @Transactional
public class FiscalObligationSync {
 private final JdbcTemplate jdbc;
 public boolean hasActiveInvoiceObligations(Long school,Long expense){return jdbc.queryForObject("SELECT count(*) FROM school_tax_obligations t JOIN school_payables p ON p.id=t.payable_id WHERE t.school_id=? AND t.expense_id=? AND NOT p.cancelled",Integer.class,school,expense)>0;}
 public void payroll(Long school,int year,Long actor){
  jdbc.queryForList("SELECT pg_advisory_xact_lock(hashtextextended(?,0))","school-taxes:"+school);
  for(var row:jdbc.queryForList("SELECT period,sum(payroll_iuts) iuts,sum(payroll_tpa) tpa,sum(payroll_vacation_tax) vacation FROM school_payables WHERE school_id=? AND period BETWEEN ? AND ? AND source IN ('STAFF_SALARY','TEACHER_SALARY') AND NOT cancelled GROUP BY period",school,LocalDate.of(year,1,1),LocalDate.of(year,12,31))){
   LocalDate period=((java.sql.Date)row.get("period")).toLocalDate();
   for(String code:List.of("IUTS","TPA","OTHER")){
    BigDecimal amount=(BigDecimal)row.get(code.equals("IUTS")?"iuts":code.equals("TPA")?"tpa":"vacation");
    if(code.equals("OTHER")&&amount.signum()==0)continue;
    int manual=jdbc.queryForObject("SELECT count(*) FROM school_tax_obligations t JOIN school_payables p ON p.id=t.payable_id WHERE t.school_id=? AND t.code=? AND t.origin='MANUAL' AND NOT p.cancelled AND t.period_start<=? AND t.period_end>=?",Integer.class,school,code,period.withDayOfMonth(period.lengthOfMonth()),period);
    if(manual>0)continue;
    upsert(school,"PAYROLL",null,code,period,period.withDayOfMonth(period.lengthOfMonth()),amount,period.plusMonths(1).withDayOfMonth(10),code+" · salaires "+period.toString().substring(0,7),null,actor);
   }
  }
 }
 public void invoice(Long school,Long expense,Invoice invoice,LocalDate paymentDate,Long actor){
  jdbc.queryForList("SELECT pg_advisory_xact_lock(hashtextextended(?,0))","school-taxes:"+school);
  Map<String,BigDecimal> totals=new TreeMap<>();
  if(invoice!=null)for(var tax:invoice.taxes())if(tax.treatment()==TaxTreatment.WITHHELD)totals.merge(tax.code().name(),tax.amount(),BigDecimal::add);
  for(var existing:jdbc.queryForList("SELECT code FROM school_tax_obligations WHERE school_id=? AND expense_id=? AND origin='EXPENSE'",school,expense))totals.putIfAbsent((String)existing.get("code"),BigDecimal.ZERO);
  for(var entry:totals.entrySet())upsert(school,"EXPENSE",expense,entry.getKey(),paymentDate,paymentDate,entry.getValue(),invoice==null?paymentDate:invoice.taxDueDate(),entry.getKey()+" retenu · facture "+(invoice==null?expense:invoice.number()),invoice==null?null:invoice.number(),actor);
 }
 private void upsert(Long school,String origin,Long expense,String code,LocalDate start,LocalDate end,BigDecimal amount,LocalDate due,String label,String reference,Long actor){
  if(label.length()>120)label=label.substring(0,120);
  String condition=origin.equals("PAYROLL")?"period_start=?":"expense_id=?";
  Object key=origin.equals("PAYROLL")?start:expense;
  var ids=jdbc.queryForList("SELECT payable_id FROM school_tax_obligations WHERE school_id=? AND origin=? AND code=? AND "+condition,Long.class,school,origin,code,key);
  if(ids.isEmpty()){
   Long category=jdbc.queryForList("SELECT id FROM expense_categories WHERE school_id=? AND system_code='TAXES'",Long.class,school).stream().findFirst().orElseGet(()->jdbc.queryForObject("INSERT INTO expense_categories(school_id,name,system_code) VALUES(?,'Reversements fiscaux automatiques','TAXES') RETURNING id",Long.class,school));
   UUID request=UUID.randomUUID();Long id=jdbc.queryForObject("INSERT INTO school_payables(school_id,category_id,source,source_key,period,due_date,amount,label,supplier,notes,created_by) VALUES(?,?,'TAX',?,?,?,?,?,'Direction générale des impôts',?,?) RETURNING id",Long.class,school,category,"tax:"+request,start.withDayOfMonth(1),due,amount,label,"Obligation alimentée automatiquement ; vérifier les paramètres et références avant déclaration.",actor);
   jdbc.update("INSERT INTO school_tax_obligations(payable_id,school_id,code,period_start,period_end,origin,declaration_reference,request_id,expense_id) VALUES(?,?,?,?,?,?,?,?,?)",id,school,code,start,end,origin,reference,request,expense);
   event(school,id,actor,"Création automatique");
  }else{
   Long id=ids.getFirst();jdbc.queryForList("SELECT id FROM school_payables WHERE id=? FOR UPDATE",Long.class,id);
   BigDecimal paid=jdbc.queryForObject("SELECT COALESCE(sum(amount),0) FROM school_payable_payments WHERE school_id=? AND payable_id=?",BigDecimal.class,school,id);
   if(amount.compareTo(paid)<0)throw new IllegalArgumentException("La correction fiscale est inférieure aux reversements déjà payés. Régularisez le règlement fiscal avant de modifier la facture ou la paie.");
   BigDecimal previous=jdbc.queryForObject("SELECT amount FROM school_payables WHERE id=?",BigDecimal.class,id);
   boolean cancelled=Boolean.TRUE.equals(jdbc.queryForObject("SELECT cancelled FROM school_payables WHERE id=?",Boolean.class,id));
   boolean invoiceChanged=origin.equals("EXPENSE")&&jdbc.queryForObject("SELECT count(*) FROM school_tax_obligations t JOIN school_payables p ON p.id=t.payable_id WHERE t.payable_id=? AND (t.period_start IS DISTINCT FROM ?::date OR t.period_end IS DISTINCT FROM ?::date OR t.declaration_reference IS DISTINCT FROM ?::text OR p.due_date IS DISTINCT FROM ?::date)",Integer.class,id,start,end,reference,due)>0;
   if(previous.compareTo(amount)!=0||cancelled||invoiceChanged){
    if(origin.equals("EXPENSE")){
     jdbc.update("UPDATE school_payables SET amount=?,cancelled=false,due_date=?,label=?,period=? WHERE id=?",amount,due,label,start.withDayOfMonth(1),id);
     jdbc.update("UPDATE school_tax_obligations SET version=version+1,period_start=?,period_end=?,declaration_reference=? WHERE payable_id=?",start,end,reference,id);
    }else{
     jdbc.update("UPDATE school_payables SET amount=? WHERE id=?",amount,id);
     jdbc.update("UPDATE school_tax_obligations SET version=version+1 WHERE payable_id=?",id);
    }
    event(school,id,actor,"Actualisation après action explicite");
   }
  }
 }
 private void event(Long school,Long id,Long actor,String reason){jdbc.update("INSERT INTO school_tax_events(school_id,payable_id,action,amount,due_date,snapshot,reason,created_by) SELECT ?,p.id,'REFRESH',p.amount,p.due_date,jsonb_build_object('amount',p.amount,'dueDate',p.due_date),?,? FROM school_payables p WHERE p.id=?",school,reason,actor,id);}
}
