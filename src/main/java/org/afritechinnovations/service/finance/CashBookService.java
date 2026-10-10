package org.afritechinnovations.service.finance;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.afritechinnovations.dto.finance.CashBookDto.*;
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
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Clock;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.Objects;

@Service
@RequiredArgsConstructor
@Transactional
public class CashBookService {
    private final JdbcTemplate jdbc;
    private final SchoolRepository schools;
    private final SchoolPermissions permissions;
    private final ObjectMapper mapper;
    private Clock clock = Clock.systemDefaultZone();
    void setClock(Clock value) { clock = value; }

    private void authorize(Long schoolId, Long actor, boolean admin) {
        var school = schools.findById(schoolId).orElseThrow(() -> new IllegalArgumentException("Établissement introuvable"));
        if (!admin && (school.getOwner()==null || !school.getOwner().getId().equals(actor))
                && !permissions.staffAllows(schoolId,actor,StaffModule.EXPENSES))
            throw new AccessDeniedException("Accès à la caisse de cet établissement refusé");
    }

    public void open(Long schoolId, Open request, Long actor, boolean admin) {
        authorize(schoolId,actor,admin);
        jdbc.queryForObject("SELECT id FROM schools WHERE id=? FOR UPDATE",Long.class,schoolId);
        if (book(schoolId,false)!=null) throw new IllegalArgumentException("Une caisse existe déjà pour cet établissement");
        if (request.date()==null || request.date().isAfter(LocalDate.now(clock)))
            throw new IllegalArgumentException("La date d’ouverture ne peut pas être dans le futur");
        jdbc.update("INSERT INTO school_cash_books(school_id,opened_on,opening_balance,created_by) VALUES(?,?,?,?)",
                schoolId,request.date(),money(request.openingBalance(),false),actor);
    }

    @Transactional(readOnly=true,isolation=Isolation.REPEATABLE_READ)
    public Overview overview(Long schoolId, String month, Long actor, boolean admin) {
        authorize(schoolId,actor,admin);
        Book book=book(schoolId,false);
        YearMonth selected=period(month);
        if(book!=null && selected.isBefore(YearMonth.from(book.openedOn()))) selected=YearMonth.from(book.openedOn());
        if(book!=null && book.closedOn()!=null && selected.isAfter(YearMonth.from(book.closedOn()))) selected=YearMonth.from(book.closedOn());
        return report(schoolId,selected,book);
    }

    public void add(Long schoolId, Entry request, Long actor, boolean admin) {
        authorize(schoolId,actor,admin);
        Book book = requireBook(schoolId,true);
        var amount = money(request.amount(),true);
        if (request.requestId()==null || request.direction()==null || request.date()==null
                || request.label()==null || request.label().isBlank() || request.label().trim().length()>150
                || request.reference()!=null && request.reference().length()>100)
            throw new IllegalArgumentException("Renseignez la date, le type et le libellé du mouvement");
        // An HTTP retry must not create a second cash movement, even after closure.
        var existing = jdbc.queryForList("SELECT * FROM cash_book_entries WHERE request_id=?",request.requestId());
        if (!existing.isEmpty()) {
            var row = existing.getFirst();
            if (!schoolId.equals(((Number)row.get("school_id")).longValue())
                    || !request.date().equals(((java.sql.Date)row.get("entry_date")).toLocalDate())
                    || !request.direction().name().equals(row.get("direction"))
                    || amount.compareTo((BigDecimal)row.get("amount"))!=0
                    || !request.label().trim().equals(row.get("label"))
                    || !Objects.equals(clean(request.reference()),row.get("reference")))
                throw new IllegalArgumentException("Cette référence de saisie correspond déjà à un autre mouvement");
            return;
        }
        writable(schoolId,book,request.date());
        jdbc.update("""
            INSERT INTO cash_book_entries(school_id,request_id,entry_date,direction,amount,label,reference,created_by)
            VALUES(?,?,?,?,?,?,?,?)
            """,schoolId,request.requestId(),request.date(),request.direction().name(),amount,request.label().trim(),clean(request.reference()),actor);
    }

    public void delete(Long schoolId, Long id, Long actor, boolean admin) {
        authorize(schoolId,actor,admin);
        Book book = requireBook(schoolId,true);
        var dates = jdbc.query("SELECT entry_date FROM cash_book_entries WHERE school_id=? AND id=?",
                (rs,n)->rs.getDate(1).toLocalDate(),schoolId,id);
        if (dates.isEmpty()) throw new IllegalArgumentException("Mouvement manuel introuvable");
        writable(schoolId,book,dates.getFirst());
        jdbc.update("DELETE FROM cash_book_entries WHERE school_id=? AND id=?",schoolId,id);
    }

    public void close(Long schoolId, Close request, Long actor, boolean admin) {
        authorize(schoolId,actor,admin);
        Book book = requireBook(schoolId,true);
        if (book.closedOn()!=null) throw new IllegalArgumentException("Cette caisse est déjà clôturée");
        YearMonth month = period(request.month());
        if (month.isAfter(YearMonth.now(clock)) || month.isBefore(YearMonth.from(book.openedOn())))
            throw new IllegalArgumentException("Le mois à clôturer est hors de la période de caisse");
        var latest = jdbc.query("SELECT month FROM cash_book_month_closures WHERE school_id=? ORDER BY month DESC LIMIT 1",
                (rs,n)->YearMonth.from(rs.getDate(1).toLocalDate()),schoolId);
        YearMonth next = latest.isEmpty() ? YearMonth.from(book.openedOn()) : latest.getFirst().plusMonths(1);
        if (!month.equals(next)) throw new IllegalArgumentException("Clôturez les mois dans l’ordre, à partir de " + next);
        if (!request.finalClosure() && !month.isBefore(YearMonth.now(clock)))
            throw new IllegalArgumentException("La clôture mensuelle est disponible après la fin du mois. Pour arrêter la caisse aujourd’hui, utilisez la clôture définitive.");
        LocalDate end = month.atEndOfMonth().isAfter(LocalDate.now(clock)) ? LocalDate.now(clock) : month.atEndOfMonth();
        if (request.finalClosure()) {
            var future = jdbc.queryForObject("SELECT COUNT(*) FROM school_cash_movements WHERE school_id=? AND entry_date>?",Long.class,schoolId,end);
            if (future!=null && future>0) throw new IllegalArgumentException("Des mouvements existent après ce mois. Clôturez la caisse après son dernier mouvement.");
        }
        Overview report = report(schoolId,month,book);
        if(report.openingBalance().signum()<0 || report.rows().stream().anyMatch(r->r.balance().signum()<0))
            throw new IllegalArgumentException("Un solde de caisse devient négatif pendant ce mois. Vérifiez les dates, les dépôts et les paiements avant de clôturer.");
        var counted = money(request.countedBalance(),false);
        if (counted.compareTo(report.closingBalance())!=0)
            throw new IllegalArgumentException("Le montant compté doit correspondre au solde calculé. Corrigez les mouvements ou enregistrez une régularisation avant de clôturer.");
        if (request.note()!=null && request.note().length()>500) throw new IllegalArgumentException("La note ne peut pas dépasser 500 caractères");
        jdbc.update("""
            INSERT INTO cash_book_month_closures(school_id,month,opening_balance,receipts,payments,closing_balance,counted_balance,note,closed_by,entries)
            VALUES(?,?,?,?,?,?,?,?,?,?::jsonb)
            """,schoolId,month.atDay(1),report.openingBalance(),report.receipts(),report.payments(),report.closingBalance(),counted,clean(request.note()),actor,json(report.rows()));
        if (request.finalClosure()) jdbc.update("UPDATE school_cash_books SET closed_on=?,closed_by=? WHERE school_id=?",end,actor,schoolId);
    }

    private Overview report(Long schoolId, YearMonth month, Book book) {
        if (book==null) return new Overview(null,month.toString(),BigDecimal.ZERO,BigDecimal.ZERO,BigDecimal.ZERO,BigDecimal.ZERO,null,List.of(),List.of());
        if (month.isBefore(YearMonth.from(book.openedOn())) || book.closedOn()!=null && month.isAfter(YearMonth.from(book.closedOn())))
            throw new IllegalArgumentException("Choisissez un mois compris dans la période de caisse");
        List<Closure> history = jdbc.query("SELECT * FROM cash_book_month_closures WHERE school_id=? ORDER BY month DESC",(rs,n)->closure(rs),schoolId);
        var closed = history.stream().filter(c->c.month().equals(month.toString())).findFirst().orElse(null);
        if (closed!=null) {
            String entries = jdbc.queryForObject("SELECT entries::text FROM cash_book_month_closures WHERE school_id=? AND month=?",String.class,schoolId,month.atDay(1));
            return new Overview(book,month.toString(),closed.openingBalance(),closed.receipts(),closed.payments(),closed.closingBalance(),closed,parseRows(entries),history);
        }
        var previous = history.stream().filter(c->c.month().compareTo(month.toString())<0).findFirst().orElse(null);
        LocalDate from = previous==null ? book.openedOn() : YearMonth.parse(previous.month()).plusMonths(1).atDay(1);
        BigDecimal base = previous==null ? book.openingBalance() : previous.closingBalance();
        BigDecimal carried = jdbc.queryForObject("""
            SELECT COALESCE(SUM(CASE WHEN direction='IN' THEN amount ELSE -amount END),0)
            FROM school_cash_movements WHERE school_id=? AND entry_date>=? AND entry_date<?
            """,BigDecimal.class,schoolId,from,month.atDay(1));
        BigDecimal opening = base.add(carried);
        LocalDate start = month.atDay(1).isBefore(book.openedOn()) ? book.openedOn() : month.atDay(1);
        LocalDate end = book.closedOn()!=null && book.closedOn().isBefore(month.atEndOfMonth()) ? book.closedOn() : month.atEndOfMonth();
        var rows = jdbc.query("""
            SELECT *, ? + SUM(CASE WHEN direction='IN' THEN amount ELSE -amount END)
                OVER (ORDER BY entry_date, CASE WHEN direction='IN' THEN 0 ELSE 1 END, source,id ROWS UNBOUNDED PRECEDING) balance
            FROM school_cash_movements WHERE school_id=? AND entry_date BETWEEN ? AND ?
            ORDER BY entry_date,CASE WHEN direction='IN' THEN 0 ELSE 1 END,source,id
            """,(rs,n)->new Movement(rs.getLong("id"),rs.getDate("entry_date").toLocalDate(),Direction.valueOf(rs.getString("direction")),
                rs.getBigDecimal("amount"),rs.getString("label"),rs.getString("reference"),rs.getString("source"),rs.getBigDecimal("balance")),opening,schoolId,start,end);
        BigDecimal receipts = rows.stream().filter(r->r.direction()==Direction.IN).map(Movement::amount).reduce(BigDecimal.ZERO,BigDecimal::add);
        BigDecimal payments = rows.stream().filter(r->r.direction()==Direction.OUT).map(Movement::amount).reduce(BigDecimal.ZERO,BigDecimal::add);
        return new Overview(book,month.toString(),opening,receipts,payments,opening.add(receipts).subtract(payments),null,rows,history);
    }

    private Book book(Long schoolId, boolean lock) {
        var books = jdbc.query("SELECT * FROM school_cash_books WHERE school_id=?"+(lock?" FOR UPDATE":""),
                (rs,n)->new Book(rs.getDate("opened_on").toLocalDate(),rs.getBigDecimal("opening_balance"),rs.getDate("closed_on")==null?null:rs.getDate("closed_on").toLocalDate()),schoolId);
        return books.isEmpty()?null:books.getFirst();
    }
    private Book requireBook(Long schoolId,boolean lock) {
        Book book=book(schoolId,lock);
        if(book==null) throw new IllegalArgumentException("Ouvrez la caisse avant de saisir des mouvements");
        return book;
    }
    private void writable(Long schoolId,Book book,LocalDate date) {
        if (book.closedOn()!=null) throw new IllegalArgumentException("Cette caisse est clôturée et consultable uniquement");
        if (date.isBefore(book.openedOn()) || date.isAfter(LocalDate.now(clock)))
            throw new IllegalArgumentException("La date doit être comprise entre l’ouverture de la caisse et aujourd’hui");
        Long closed=jdbc.queryForObject("SELECT COUNT(*) FROM cash_book_month_closures WHERE school_id=? AND month=?",Long.class,schoolId,YearMonth.from(date).atDay(1));
        if (closed!=null && closed>0) throw new IllegalArgumentException("Ce mois de caisse est clôturé et consultable uniquement");
    }
    private static Closure closure(ResultSet rs) throws SQLException {
        return new Closure(YearMonth.from(rs.getDate("month").toLocalDate()).toString(),rs.getBigDecimal("opening_balance"),
                rs.getBigDecimal("receipts"),rs.getBigDecimal("payments"),rs.getBigDecimal("closing_balance"),rs.getBigDecimal("counted_balance"),rs.getString("note"),rs.getTimestamp("closed_at").toLocalDateTime());
    }
    static YearMonth period(String value) {
        try { if(value==null || !value.matches("\\d{4}-(0[1-9]|1[0-2])")) throw new IllegalArgumentException(); return YearMonth.parse(value); }
        catch(RuntimeException ex) { throw new IllegalArgumentException("Mois invalide : utilisez YYYY-MM"); }
    }
    static BigDecimal money(BigDecimal value,boolean positive) {
        if(value==null || value.signum()<0 || positive && value.signum()==0 || value.compareTo(new BigDecimal(positive?"9999999999.99":"999999999999.99"))>0)
            throw new IllegalArgumentException("Montant invalide");
        try { return value.setScale(2,RoundingMode.UNNECESSARY); }
        catch(ArithmeticException ex) { throw new IllegalArgumentException("Utilisez au maximum deux décimales"); }
    }
    private String json(List<Movement> rows) {
        try { return mapper.writeValueAsString(rows); } catch(JsonProcessingException ex) { throw new IllegalStateException("Impossible d’archiver le brouillard",ex); }
    }
    private List<Movement> parseRows(String value) {
        try { return mapper.readValue(value,new TypeReference<>(){}); } catch(JsonProcessingException ex) { throw new IllegalStateException("Impossible de lire le brouillard archivé",ex); }
    }
    private static String clean(String value) { return value==null || value.isBlank()?null:value.trim(); }
}
