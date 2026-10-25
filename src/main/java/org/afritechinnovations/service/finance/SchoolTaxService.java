package org.afritechinnovations.service.finance;

import lombok.RequiredArgsConstructor;
import org.afritechinnovations.dto.finance.PayableDto;
import org.afritechinnovations.dto.finance.SchoolTaxDto.*;
import org.afritechinnovations.model.common.*;
import org.afritechinnovations.repository.common.SchoolRepository;
import org.afritechinnovations.security.SchoolPermissions;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;

@Service
@RequiredArgsConstructor
@Transactional
public class SchoolTaxService {
    private final SchoolRepository schools;
    private final SchoolPermissions permissions;
    private final JdbcTemplate jdbc;
    private final SchoolPayableService payables;
    private final OwnerExpenseService expenses;
    private final AutomaticPayrollTaxService automaticTaxes;

    /** Explicit write operation; GET overview remains read-only. */
    public Overview synchronizePayroll(Long school,int year,Long actor,boolean admin) {
        authorize(school,actor,admin,true);checkYear(year);lock(school);
        if(year==LocalDate.now().getYear())payables.prepareSalaries(school,YearMonth.now().toString(),actor);
        automaticTaxes.existing(school,year,actor);
        return overview(school,year,actor,admin);
    }

    private void authorize(Long school,Long actor,boolean admin,boolean write) {
        var entity=schools.findById(school).orElseThrow(()->new IllegalArgumentException("Établissement introuvable."));
        if(!admin && (entity.getOwner()==null||!entity.getOwner().getId().equals(actor)) && !permissions.staffAllows(school,actor,StaffModule.TAXES))
            throw new AccessDeniedException("Accès aux impôts et taxes de cet établissement refusé.");
        if(write && entity.getStatus()!=SchoolStatus.ACTIVE) throw new IllegalArgumentException("L’établissement doit être actif.");
    }
    private void lock(Long school) {jdbc.queryForList("SELECT pg_advisory_xact_lock(hashtextextended(?,0))","school-taxes:"+school);}
    private void lockPayable(Long school,Long id) {
        if(jdbc.queryForList("SELECT p.id FROM school_payables p JOIN school_tax_obligations t ON t.payable_id=p.id AND t.school_id=p.school_id WHERE p.school_id=? AND p.id=? FOR UPDATE OF p",Long.class,school,id).isEmpty())
            throw new IllegalArgumentException("Impôt ou taxe introuvable.");
    }
    @Transactional(readOnly=true,isolation=org.springframework.transaction.annotation.Isolation.REPEATABLE_READ)
    public Overview overview(Long school,int year,Long actor,boolean admin) {
        authorize(school,actor,admin,false);checkYear(year);
        var all=jdbc.queryForList("SELECT payable_id FROM school_tax_obligations WHERE school_id=? AND period_start<=? ORDER BY period_start DESC,payable_id DESC",Long.class,school,LocalDate.of(year,12,31));
        var rows=all.stream().map(id->row(school,id,year)).filter(r->r.periodEnd().getYear()>=year || !r.obligation().status().equals("CANCELLED")&&r.obligation().remaining().signum()>0).toList();
        var active=rows.stream().map(Row::obligation).filter(p->!p.status().equals("CANCELLED")).toList();
        return new Overview(year,rows,sum(active.stream().map(PayableDto.Row::amount).toList()),sum(active.stream().map(PayableDto.Row::paid).toList()),
                sum(active.stream().map(PayableDto.Row::remaining).toList()),sum(active.stream().filter(PayableDto.Row::overdue).map(PayableDto.Row::remaining).toList()),SupplierVatReport.read(jdbc,school,year));
    }
    @Transactional(readOnly=true,isolation=org.springframework.transaction.annotation.Isolation.REPEATABLE_READ)
    public Detail detail(Long school,Long id,Long actor,boolean admin) {
        authorize(school,actor,admin,false);Row row=row(school,id,LocalDate.now().getYear());
        var events=jdbc.query("""
            SELECT e.*,concat(u.first_name,' ',u.last_name) author FROM school_tax_events e LEFT JOIN users u ON u.id=e.created_by
            WHERE e.school_id=? AND e.payable_id=? ORDER BY e.id DESC
            """,(rs,n)->new Event(rs.getLong("id"),rs.getString("action"),rs.getBigDecimal("amount"),rs.getDate("due_date").toLocalDate(),rs.getString("reason"),rs.getString("author"),rs.getTimestamp("created_at").toLocalDateTime()),school,id);
        return new Detail(row,payables.payments(school,id,actor,true),events);
    }
    public Row create(Long school,Create input,Long actor,boolean admin) {
        authorize(school,actor,admin,true);lock(school);period(input.periodStart(),input.periodEnd());
        BigDecimal amount=amount(input.amount());
        var previous=jdbc.queryForList("SELECT payable_id FROM school_tax_obligations WHERE school_id=? AND request_id=?",Long.class,school,input.requestId());
        if(!previous.isEmpty()) {
            Row existing=row(school,previous.getFirst(),input.periodStart().getYear());
            if(existing.code()!=input.code()||!existing.periodStart().equals(input.periodStart())||!existing.periodEnd().equals(input.periodEnd())
                    || !existing.obligation().label().equals(input.label().trim())||existing.obligation().amount().compareTo(amount)!=0
                    || !existing.obligation().dueDate().equals(input.dueDate())||!Objects.equals(existing.obligation().supplier(),input.creditor().trim())
                    || !Objects.equals(existing.declarationReference(),clean(input.declarationReference()))||!Objects.equals(existing.obligation().notes(),clean(input.notes())))
                throw new IllegalArgumentException("Cette référence de requête correspond déjà à une autre obligation.");
            return existing;
        }
        rejectOverlap(school,input.code(),input.periodStart(),input.periodEnd(),input.label().trim());
        Long id=insert(school,input.code(),input.label().trim(),input.periodStart(),input.periodEnd(),amount,input.dueDate(),input.creditor().trim(),clean(input.declarationReference()),clean(input.notes()),"MANUAL",input.requestId(),actor);
        event(school,id,"CREATE","Obligation saisie manuellement",actor);
        return row(school,id,input.periodStart().getYear());
    }
    public Row edit(Long school,Long id,Edit input,Long actor,boolean admin) {
        authorize(school,actor,admin,true);lock(school);lockPayable(school,id);Row previous=row(school,id,LocalDate.now().getYear());
        requireEditable(previous,input.version());BigDecimal amount=amount(input.amount());
        if(previous.origin().equals("PAYROLL")&&amount.compareTo(previous.obligation().amount())!=0)
            throw new IllegalArgumentException("Actualisez les montants issus des salaires depuis la reprise IUTS / TPA.");
        jdbc.update("UPDATE school_payables SET amount=?,due_date=?,supplier=?,notes=? WHERE school_id=? AND id=?",amount,input.dueDate(),input.creditor().trim(),clean(input.notes()),school,id);
        jdbc.update("UPDATE school_tax_obligations SET declaration_reference=?,version=version+1 WHERE school_id=? AND payable_id=?",clean(input.declarationReference()),school,id);
        event(school,id,"EDIT",input.reason().trim(),actor);return row(school,id,previous.periodStart().getYear());
    }
    public void cancel(Long school,Long id,Cancel input,Long actor,boolean admin) {
        authorize(school,actor,admin,true);lock(school);lockPayable(school,id);Row previous=row(school,id,LocalDate.now().getYear());
        requireEditable(previous,input.version());
        if(previous.origin().equals("PAYROLL")) throw new IllegalArgumentException("Une reprise de salaires se corrige en actualisant son montant depuis les salaires.");
        jdbc.update("UPDATE school_payables SET cancelled=true WHERE school_id=? AND id=?",school,id);
        jdbc.update("UPDATE school_tax_obligations SET version=version+1 WHERE school_id=? AND payable_id=?",school,id);
        event(school,id,"CANCEL",input.reason().trim(),actor);
    }
    public PayableDto.Row pay(Long school,Long id,PayableDto.Pay input,Long actor,boolean admin) {
        authorize(school,actor,admin,true);row(school,id,LocalDate.now().getYear());return payables.pay(school,id,input,actor,true);
    }
    @Transactional(readOnly=true)
    public PayrollPreview preview(Long school,String month,Long actor,boolean admin) {authorize(school,actor,admin,false);return payroll(school,month);}
    public void importPayroll(Long school,ImportPayroll input,Long actor,boolean admin) {
        authorize(school,actor,admin,true);lock(school);
        var preview=payroll(school,input.month());YearMonth month=YearMonth.parse(preview.month());
        if(month.isAfter(YearMonth.now())) throw new IllegalArgumentException("La reprise d’un mois futur n’est pas disponible.");
        for(Code code:List.of(Code.IUTS,Code.TPA,Code.OTHER)) {
            BigDecimal amount=code==Code.IUTS?preview.iuts():code==Code.TPA?preview.tpa():preview.vacationWithholding();
            var existing=jdbc.queryForList("SELECT payable_id FROM school_tax_obligations WHERE school_id=? AND code=? AND period_start=? AND origin='PAYROLL'",Long.class,school,code.name(),month.atDay(1));
            if(existing.isEmpty()) {
                if(amount.signum()==0) continue;
                rejectOverlap(school,code,month.atDay(1),month.atEndOfMonth(),code.name());
                Long id=insert(school,code,(code==Code.OTHER?"Retenues sur vacations d’enseignement":code.name())+" · salaires "+month,month.atDay(1),month.atEndOfMonth(),amount,input.dueDate(),input.creditor().trim(),clean(input.declarationReference()),"Montant repris des calculs validés ou saisies de paie ; à vérifier avant déclaration.","PAYROLL",UUID.randomUUID(),actor);
                event(school,id,"IMPORT",input.reason().trim(),actor);
            } else {
                Long id=existing.getFirst();lockPayable(school,id);Row old=row(school,id,month.getYear());
                if(amount.compareTo(old.obligation().paid())<0) throw new IllegalArgumentException("Le montant recalculé est inférieur au total déjà réglé pour "+code+". Vérifiez les salaires et paiements avec votre comptable.");
                if(amount.compareTo(old.obligation().amount())!=0) {
                    jdbc.update("UPDATE school_payables SET amount=? WHERE school_id=? AND id=?",amount,school,id);
                    jdbc.update("UPDATE school_tax_obligations SET version=version+1 WHERE school_id=? AND payable_id=?",school,id);
                    event(school,id,"REFRESH",input.reason().trim(),actor);
                }
            }
        }
    }
    private PayrollPreview payroll(Long school,String value) {
        YearMonth month;try{month=YearMonth.parse(value);checkYear(month.getYear());}catch(RuntimeException error){throw new IllegalArgumentException("Choisissez un mois valide.");}
        return jdbc.queryForObject("SELECT COALESCE(sum(payroll_iuts),0) iuts,COALESCE(sum(payroll_tpa),0) tpa,COALESCE(sum(payroll_vacation_tax),0) vacation,count(*) count FROM school_payables WHERE school_id=? AND period=? AND source IN ('STAFF_SALARY','TEACHER_SALARY') AND NOT cancelled",
                (rs,n)->new PayrollPreview(month.toString(),rs.getBigDecimal("iuts"),rs.getBigDecimal("tpa"),rs.getInt("count"),rs.getBigDecimal("vacation")),school,month.atDay(1));
    }
    private Row row(Long school,Long id,int year) {
        var rows=jdbc.query("SELECT * FROM school_tax_obligations WHERE school_id=? AND payable_id=?",(rs,n)->new Row(rs.getLong("payable_id"),Code.valueOf(rs.getString("code")),rs.getDate("period_start").toLocalDate(),rs.getDate("period_end").toLocalDate(),rs.getString("origin"),rs.getString("declaration_reference"),rs.getLong("version"),rs.getDate("period_end").toLocalDate().getYear()<year,payables.taxObligation(school,id)),school,id);
        return rows.stream().findFirst().orElseThrow(()->new IllegalArgumentException("Impôt ou taxe introuvable."));
    }
    private Long insert(Long school,Code code,String label,LocalDate start,LocalDate end,BigDecimal amount,LocalDate due,String creditor,String reference,String notes,String origin,UUID key,Long actor) {
        var categories=expenses.listCategories(school,actor,true);
        Long category=categories.stream().filter(c->"TAXES".equals(c.systemCode())).map(c->c.id()).findFirst().orElseGet(()->{
            var existing=categories.stream().filter(c->c.active()&&c.name().equalsIgnoreCase("Impôts & taxes")&&c.systemCode()==null).findFirst();
            if(existing.isPresent()){jdbc.update("UPDATE expense_categories SET system_code='TAXES' WHERE id=?",existing.get().id());return existing.get().id();}
            return jdbc.queryForObject("INSERT INTO expense_categories(school_id,name,system_code) VALUES(?,'Règlements fiscaux','TAXES') RETURNING id",Long.class,school);
        });
        Long id=jdbc.queryForObject("INSERT INTO school_payables(school_id,category_id,source,source_key,period,due_date,amount,label,supplier,notes,created_by) VALUES(?,?,'TAX',?,?,?,?,?,?,?,?) RETURNING id",Long.class,
                school,category,"tax:"+key,start.withDayOfMonth(1),due,amount,label,creditor,notes,actor);
        jdbc.update("INSERT INTO school_tax_obligations(payable_id,school_id,code,period_start,period_end,origin,declaration_reference,request_id) VALUES(?,?,?,?,?,?,?,?)",id,school,code.name(),start,end,origin,reference,key);
        return id;
    }
    private void rejectOverlap(Long school,Code code,LocalDate start,LocalDate end,String label) {
        int count=jdbc.queryForObject("""
            SELECT count(*) FROM school_tax_obligations t JOIN school_payables p ON p.id=t.payable_id AND p.school_id=t.school_id
            WHERE t.school_id=? AND t.code=? AND NOT p.cancelled AND t.period_start<=? AND t.period_end>=?
            AND (? IN ('IUTS','TPA') OR (t.period_start=? AND t.period_end=? AND lower(p.label)=lower(?)))
            """,Integer.class,school,code.name(),end,start,code.name(),start,end,label);
        if(count>0) throw new IllegalArgumentException("Une obligation de ce type existe déjà pour cette période. Actualisez ou consultez sa fiche pour éviter un doublon.");
    }
    private void event(Long school,Long id,String action,String reason,Long actor) {
        jdbc.update("""
            INSERT INTO school_tax_events(school_id,payable_id,action,amount,due_date,snapshot,reason,created_by)
            SELECT p.school_id,p.id,?,p.amount,p.due_date,jsonb_build_object('obligation',to_jsonb(p),'tax',to_jsonb(t)),?,?
            FROM school_payables p JOIN school_tax_obligations t ON t.payable_id=p.id AND t.school_id=p.school_id WHERE p.school_id=? AND p.id=?
            """,action,reason,actor,school,id);
    }
    private static void requireEditable(Row row,long version) {
        if(row.version()!=version) throw new IllegalArgumentException("La fiche a changé. Actualisez avant de réessayer.");
        if(row.obligation().paid().signum()>0||row.obligation().status().equals("CANCELLED")) throw new IllegalArgumentException("Une obligation déjà réglée ou annulée ne peut pas être modifiée.");
    }
    private static BigDecimal amount(BigDecimal value) {
        if(value==null||value.signum()<0||value.precision()-value.scale()>10) throw new IllegalArgumentException("Montant fiscal invalide.");
        try{return value.setScale(2,java.math.RoundingMode.UNNECESSARY);}catch(ArithmeticException error){throw new IllegalArgumentException("Deux décimales maximum.");}
    }
    private static void period(LocalDate start,LocalDate end){if(start==null||end==null||end.isBefore(start))throw new IllegalArgumentException("Période fiscale invalide.");checkYear(start.getYear());checkYear(end.getYear());}
    private static void checkYear(int year){if(year<1900||year>2200)throw new IllegalArgumentException("Choisissez une année entre 1900 et 2200.");}
    private static String clean(String value){return value==null||value.isBlank()?null:value.trim();}
    private static BigDecimal sum(List<BigDecimal> amounts){return amounts.stream().reduce(BigDecimal.ZERO,BigDecimal::add);}
}
