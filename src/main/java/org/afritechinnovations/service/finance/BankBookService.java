package org.afritechinnovations.service.finance;

import lombok.RequiredArgsConstructor;
import org.afritechinnovations.dto.finance.BankBookDto.*;
import org.afritechinnovations.model.common.StaffModule;
import org.afritechinnovations.repository.common.SchoolRepository;
import org.afritechinnovations.security.SchoolPermissions;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.Objects;

@Service @RequiredArgsConstructor @Transactional
public class BankBookService {
    private final JdbcTemplate jdbc;
    private final SchoolRepository schools;
    private final SchoolPermissions permissions;
    private Clock clock = Clock.systemDefaultZone();
    void setClock(Clock value) { clock=value; }

    private void authorize(Long schoolId,Long actor,boolean admin) {
        var school=schools.findById(schoolId).orElseThrow(()->new IllegalArgumentException("Établissement introuvable"));
        if(!admin && (school.getOwner()==null || !school.getOwner().getId().equals(actor))
                && !permissions.staffAllows(schoolId,actor,StaffModule.BANK_BOOK))
            throw new AccessDeniedException("Accès au brouillard de banque refusé");
    }
    public void open(Long schoolId,Open request,Long actor,boolean admin) {
        authorize(schoolId,actor,admin);
        jdbc.queryForObject("SELECT id FROM schools WHERE id=? FOR UPDATE",Long.class,schoolId);
        if(book(schoolId,false)!=null)throw new IllegalArgumentException("Le brouillard de banque est déjà ouvert");
        if(request.date()==null || request.date().isAfter(LocalDate.now(clock)))throw new IllegalArgumentException("La date d’ouverture ne peut pas être future");
        jdbc.update("INSERT INTO school_bank_books(school_id,opened_on,opening_balance,created_by) VALUES(?,?,?,?)",schoolId,request.date(),money(request.openingBalance(),false),actor);
    }
    @Transactional(readOnly=true,isolation=Isolation.REPEATABLE_READ)
    public Overview overview(Long schoolId,String value,Long actor,boolean admin) {
        authorize(schoolId,actor,admin);
        YearMonth month=CashBookService.period(value);
        if(month.isAfter(YearMonth.now(clock)))throw new IllegalArgumentException("Choisissez un mois passé ou actuel");
        Book book=book(schoolId,false);
        if(book==null)return new Overview(null,month.toString(),BigDecimal.ZERO,BigDecimal.ZERO,BigDecimal.ZERO,BigDecimal.ZERO,List.of());
        if(month.isBefore(YearMonth.from(book.openedOn())))month=YearMonth.from(book.openedOn());
        BigDecimal carried=jdbc.queryForObject("SELECT COALESCE(SUM(CASE WHEN direction='IN' THEN amount ELSE -amount END),0) FROM school_bank_movements WHERE school_id=? AND entry_date>=? AND entry_date<?",BigDecimal.class,schoolId,book.openedOn(),month.atDay(1));
        BigDecimal opening=book.openingBalance().add(carried);
        LocalDate start=month.atDay(1).isBefore(book.openedOn())?book.openedOn():month.atDay(1);
        LocalDate end=month.atEndOfMonth().isAfter(LocalDate.now(clock))?LocalDate.now(clock):month.atEndOfMonth();
        var rows=jdbc.query("""
            SELECT *, ? + SUM(CASE WHEN direction='IN' THEN amount ELSE -amount END)
                OVER (ORDER BY entry_date,CASE WHEN direction='IN' THEN 0 ELSE 1 END,source,id ROWS UNBOUNDED PRECEDING) balance
            FROM school_bank_movements WHERE school_id=? AND entry_date BETWEEN ? AND ?
            ORDER BY entry_date,CASE WHEN direction='IN' THEN 0 ELSE 1 END,source,id
            """,(r,n)->new Movement(r.getLong("id"),r.getDate("entry_date").toLocalDate(),Direction.valueOf(r.getString("direction")),r.getBigDecimal("amount"),r.getString("label"),r.getString("reference"),r.getString("source"),r.getBigDecimal("balance")),opening,schoolId,start,end);
        BigDecimal receipts=rows.stream().filter(r->r.direction()==Direction.IN).map(Movement::amount).reduce(BigDecimal.ZERO,BigDecimal::add);
        BigDecimal payments=rows.stream().filter(r->r.direction()==Direction.OUT).map(Movement::amount).reduce(BigDecimal.ZERO,BigDecimal::add);
        return new Overview(book,month.toString(),opening,receipts,payments,opening.add(receipts).subtract(payments),rows);
    }
    public void add(Long schoolId,Entry request,Long actor,boolean admin) {
        authorize(schoolId,actor,admin);Book book=requireBook(schoolId);
        BigDecimal amount=money(request.amount(),true);
        if(request.requestId()==null || request.date()==null || request.direction()==null || request.label()==null || request.label().isBlank() || request.label().trim().length()>150 || request.reference()!=null && request.reference().length()>100)
            throw new IllegalArgumentException("Renseignez la date, le type, le montant et le libellé");
        var existing=jdbc.queryForList("SELECT * FROM bank_book_entries WHERE request_id=?",request.requestId());
        if(!existing.isEmpty()) {
            var row=existing.getFirst();
            if(!schoolId.equals(((Number)row.get("school_id")).longValue()) || !request.date().equals(((java.sql.Date)row.get("entry_date")).toLocalDate())
                    || !request.direction().name().equals(row.get("direction")) || amount.compareTo((BigDecimal)row.get("amount"))!=0
                    || !request.label().trim().equals(row.get("label")) || !Objects.equals(clean(request.reference()),row.get("reference")))
                throw new IllegalArgumentException("Cette référence de saisie correspond déjà à un autre mouvement");
            return;
        }
        writable(book,request.date());
        jdbc.update("INSERT INTO bank_book_entries(school_id,request_id,entry_date,direction,amount,label,reference,created_by) VALUES(?,?,?,?,?,?,?,?)",schoolId,request.requestId(),request.date(),request.direction().name(),amount,request.label().trim(),clean(request.reference()),actor);
    }
    public void delete(Long schoolId,Long id,Long actor,boolean admin) {
        authorize(schoolId,actor,admin);Book book=requireBook(schoolId);
        var dates=jdbc.query("SELECT entry_date FROM bank_book_entries WHERE school_id=? AND id=?",(r,n)->r.getDate(1).toLocalDate(),schoolId,id);
        if(dates.isEmpty())throw new IllegalArgumentException("Mouvement manuel introuvable");
        writable(book,dates.getFirst());
        jdbc.update("DELETE FROM bank_book_entries WHERE school_id=? AND id=?",schoolId,id);
    }
    private Book book(Long schoolId,boolean lock) {
        return jdbc.query("SELECT opened_on,opening_balance FROM school_bank_books WHERE school_id=?"+(lock?" FOR UPDATE":""),(r,n)->new Book(r.getDate(1).toLocalDate(),r.getBigDecimal(2)),schoolId).stream().findFirst().orElse(null);
    }
    private Book requireBook(Long schoolId) {
        Book book=book(schoolId,true);if(book==null)throw new IllegalArgumentException("Ouvrez le brouillard de banque avant de saisir un mouvement");return book;
    }
    private void writable(Book book,LocalDate date) {
        if(date.isBefore(book.openedOn()) || date.isAfter(LocalDate.now(clock)))throw new IllegalArgumentException("La date doit être comprise entre l’ouverture et aujourd’hui");
    }
    static BigDecimal money(BigDecimal value,boolean positive) {
        if(value==null || positive && value.signum()<=0 || value.abs().compareTo(new BigDecimal(positive?"9999999999.99":"999999999999.99"))>0)throw new IllegalArgumentException("Montant invalide");
        try{return value.setScale(2,RoundingMode.UNNECESSARY);}catch(ArithmeticException e){throw new IllegalArgumentException("Utilisez au maximum deux décimales");}
    }
    private static String clean(String value){return value==null || value.isBlank()?null:value.trim();}
}
