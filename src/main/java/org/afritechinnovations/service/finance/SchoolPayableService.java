package org.afritechinnovations.service.finance;

import lombok.RequiredArgsConstructor;
import org.afritechinnovations.dto.finance.PayableDto;
import org.afritechinnovations.dto.people.TeacherPaymentRequest;
import org.afritechinnovations.model.common.School;
import org.afritechinnovations.model.common.StaffModule;
import org.afritechinnovations.model.finance.Expense;
import org.afritechinnovations.repository.common.SchoolRepository;
import org.afritechinnovations.repository.common.UserRepository;
import org.afritechinnovations.repository.finance.ExpenseCategoryRepository;
import org.afritechinnovations.repository.finance.ExpenseRepository;
import org.afritechinnovations.repository.people.TeacherRepository;
import org.afritechinnovations.security.SchoolPermissions;
import org.afritechinnovations.service.people.TeacherWorkService;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Clock;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional
public class SchoolPayableService {
    private final JdbcTemplate jdbc;
    private final SchoolRepository schools;
    private final UserRepository users;
    private final SchoolPermissions permissions;
    private final ExpenseCategoryRepository categories;
    private final ExpenseRepository expenses;
    private final TeacherRepository teachers;
    private final TeacherWorkService teacherWork;
    private final OwnerExpenseService expenseService;
    private Clock clock = Clock.systemDefaultZone();
    void setClock(Clock clock) { this.clock = clock; }

    private static final String SELECT = """
        SELECT p.*, c.name category_name,
          CASE WHEN p.teacher_id IS NOT NULL THEN
            (SELECT COALESCE(SUM(t.amount),0) FROM teacher_payments t WHERE t.teacher_id=p.teacher_id AND t.pay_month=p.period)
          ELSE (SELECT COALESCE(SUM(x.amount),0) FROM school_payable_payments x WHERE x.payable_id=p.id) END paid
        FROM school_payables p JOIN expense_categories c ON c.id=p.category_id
        """;

    public School requireSchool(Long schoolId, Long actor, boolean admin) {
        School school = schools.findById(schoolId).orElseThrow(() -> new IllegalArgumentException("Établissement introuvable"));
        if (!admin && (school.getOwner()==null || !school.getOwner().getId().equals(actor))
                && !permissions.staffAllows(schoolId, actor, StaffModule.EXPENSES)
                && !permissions.staffAllows(schoolId, actor, StaffModule.FINANCE)) {
            throw new AccessDeniedException("Accès aux finances de cet établissement refusé");
        }
        return school;
    }

    public List<org.afritechinnovations.dto.finance.OwnerExpenseDto.CategoryInfo> categories(Long schoolId, Long actor, boolean admin) {
        requireSchool(schoolId, actor, admin);
        return expenseService.listCategories(schoolId, actor, true);
    }

    @Transactional(readOnly=true)
    public PayableDto.Overview overview(Long schoolId, String month, boolean salariesOnly, Long actor, boolean admin) {
        requireSchool(schoolId, actor, admin);
        YearMonth period = month(month);
        List<PayableDto.Row> rows = jdbc.query(SELECT + " WHERE p.school_id=? AND p.period<=? ORDER BY p.due_date,p.id",
                (rs,n) -> row(rs), schoolId, period.atDay(1)).stream()
                .filter(r -> r.period().equals(period.toString()) || !r.status().equals("CANCELLED") && r.remaining().signum()>0).toList();
        if (salariesOnly) rows = rows.stream().filter(r -> r.source().endsWith("SALARY")).toList();
        List<PayableDto.Row> active = rows.stream().filter(r -> !r.status().equals("CANCELLED")).toList();
        List<PayableDto.FixedCharge> fixed = jdbc.query("SELECT * FROM fixed_school_charges WHERE school_id=? ORDER BY active DESC,label",
                (rs,n) -> new PayableDto.FixedCharge(rs.getLong("id"),rs.getLong("category_id"),rs.getString("label"),
                        rs.getString("supplier"),rs.getBigDecimal("amount"),rs.getInt("due_day"),
                        rs.getDate("start_month").toLocalDate().toString().substring(0,7),rs.getBoolean("active")), schoolId);
        return new PayableDto.Overview(rows, salariesOnly ? List.of() : fixed,
                sum(active.stream().map(PayableDto.Row::amount).toList()),sum(active.stream().map(PayableDto.Row::paid).toList()),
                sum(active.stream().map(PayableDto.Row::remaining).toList()),
                sum(active.stream().filter(PayableDto.Row::overdue).map(PayableDto.Row::remaining).toList()));
    }

    /** A school lock and the unique source/period key make repeated preparations safe. */
    public void prepare(Long schoolId, String month, Long actor, boolean admin) {
        requireSchool(schoolId, actor, admin);
        YearMonth period = month(month);
        jdbc.queryForObject("SELECT id FROM schools WHERE id=? FOR UPDATE", Long.class, schoolId);
        var categoryList = categories(schoolId, actor, admin);
        Long staffCategory = categoryList.stream().filter(c -> "STAFF_PAYROLL".equals(c.systemCode()))
                .map(c -> c.id()).findFirst().orElseGet(() -> {
                    Long id=jdbc.queryForObject("INSERT INTO expense_categories(school_id,name,system_code) VALUES(?,'Salaires du personnel','STAFF_PAYROLL') RETURNING id",Long.class,schoolId);
                    return id;
                });
        Long teacherCategory = categoryList.stream().filter(c -> "PAYROLL".equals(c.systemCode())).map(c -> c.id()).findFirst().orElseThrow();
        jdbc.query("""
            SELECT s.id,s.monthly_salary,u.first_name,u.last_name FROM school_staff s JOIN users u ON u.id=s.user_id
            WHERE s.school_id=? AND s.active AND u.active AND s.monthly_salary>0 AND s.created_at::date<=?
            """, (rs,n) -> {
            String name = rs.getString("first_name")+" "+rs.getString("last_name");
            insert(schoolId,staffCategory,"STAFF_SALARY","staff:"+rs.getLong("id"),period,period.atEndOfMonth(),
                    rs.getBigDecimal("monthly_salary"),"Salaire · "+name,name,null,null,actor);
            return rs.getLong("id");
        },schoolId,period.atEndOfMonth());
        for (var teacher : teachers.findBySchoolId(schoolId)) {
            if (Boolean.FALSE.equals(teacher.getUser().getActive())
                    || teacher.getHireDate()!=null && teacher.getHireDate().isAfter(period.atEndOfMonth())) continue;
            var detail = teacherWork.getTeacherDetail(teacher.getId(),period.toString(),actor,true);
            if (detail.rate()==null || detail.rate().signum()<=0) continue;
            // Hourly / attendance-based payroll remains live; a zero due may become payable later in the month.
            BigDecimal amount = detail.month().amountDue();
            String name = teacher.getUser().getFirstName()+" "+teacher.getUser().getLastName();
            insert(schoolId,teacherCategory,"TEACHER_SALARY","teacher:"+teacher.getId(),period,period.atEndOfMonth(),
                    amount,"Salaire · "+name,name,"Montant calculé selon la rémunération de l’enseignant.",teacher.getId(),actor);
        }
        jdbc.query("SELECT * FROM fixed_school_charges WHERE school_id=? AND active AND start_month<=?", (rs,n) -> {
            insert(schoolId,rs.getLong("category_id"),"FIXED","fixed:"+rs.getLong("id"),period,
                    period.atDay(Math.min(rs.getInt("due_day"),period.lengthOfMonth())),rs.getBigDecimal("amount"),
                    rs.getString("label"),rs.getString("supplier"),null,null,actor);
            return rs.getLong("id");
        },schoolId,period.atDay(1));
    }

    public void create(Long schoolId, PayableDto.Create request, Long actor, boolean admin) {
        requireSchool(schoolId,actor,admin); requireCategory(schoolId,request.categoryId());
        insert(schoolId,request.categoryId(),"MANUAL","manual:"+UUID.randomUUID(),YearMonth.from(request.dueDate()),
                request.dueDate(),money(request.amount()),request.label().trim(),clean(request.supplier()),clean(request.notes()),null,actor);
    }

    public void createFixed(Long schoolId, PayableDto.FixedRequest request, Long actor, boolean admin) {
        requireSchool(schoolId,actor,admin); requireCategory(schoolId,request.categoryId());
        if (request.dueDay()<1 || request.dueDay()>31) throw new IllegalArgumentException("Le jour doit être compris entre 1 et 31");
        jdbc.update("""
            INSERT INTO fixed_school_charges(school_id,category_id,label,supplier,amount,due_day,start_month,created_by)
            VALUES(?,?,?,?,?,?,?,?)
            """,schoolId,request.categoryId(),request.label().trim(),clean(request.supplier()),money(request.amount()),
                request.dueDay(),month(request.startMonth()).atDay(1),actor);
    }

    public void setFixedActive(Long schoolId, Long id, boolean active, Long actor, boolean admin) {
        requireSchool(schoolId,actor,admin);
        jdbc.queryForObject("SELECT id FROM schools WHERE id=? FOR UPDATE",Long.class,schoolId);
        if (jdbc.update("UPDATE fixed_school_charges SET active=? WHERE id=? AND school_id=?",active,id,schoolId)!=1)
            throw new IllegalArgumentException("Charge fixe introuvable");
    }

    @Transactional(readOnly=true)
    public List<PayableDto.Payment> payments(Long schoolId, Long id, Long actor, boolean admin) {
        requireSchool(schoolId,actor,admin); var row = find(schoolId,id,false);
        if (row.source().equals("TEACHER_SALARY")) {
            return jdbc.query("""
                SELECT t.id,t.payment_date,t.amount,x.method,t.reference FROM teacher_payments t
                JOIN school_payables p ON p.teacher_id=t.teacher_id AND p.period=t.pay_month
                LEFT JOIN school_payable_payments x ON x.teacher_payment_id=t.id WHERE p.id=? ORDER BY t.payment_date,t.id
                """,(rs,n) -> payment(rs),id);
        }
        return jdbc.query("SELECT id,payment_date,amount,method,reference FROM school_payable_payments WHERE payable_id=? ORDER BY payment_date,id",
                (rs,n) -> payment(rs),id);
    }

    public PayableDto.Row pay(Long schoolId, Long id, PayableDto.Pay request, Long actor, boolean admin) {
        School school = requireSchool(schoolId,actor,admin);
        // Lock the teacher first, using the same order as the existing payroll service.
        Long teacherId = jdbc.queryForObject("SELECT teacher_id FROM school_payables WHERE school_id=? AND id=?",Long.class,schoolId,id);
        if (teacherId!=null) teachers.findByIdForUpdate(teacherId).orElseThrow();
        var row = find(schoolId,id,true);
        var existing = jdbc.queryForList("SELECT payable_id,amount,payment_date,method,reference FROM school_payable_payments WHERE request_id=?",request.requestId());
        if (!existing.isEmpty()) {
            var x = existing.getFirst();
            if (!id.equals(((Number)x.get("payable_id")).longValue())
                    || ((BigDecimal)x.get("amount")).compareTo(request.amount())!=0
                    || !request.date().equals(((java.sql.Date)x.get("payment_date")).toLocalDate())
                    || !request.method().name().equals(x.get("method"))
                    || !java.util.Objects.equals(clean(request.reference()),x.get("reference")))
                throw new IllegalArgumentException("Cette référence de requête a déjà été utilisée pour un autre paiement");
            return row;
        }
        if (request.date().isAfter(LocalDate.now(clock))) throw new IllegalArgumentException("La date de paiement ne peut pas être future");
        BigDecimal amount = money(request.amount());
        validatePayment(row,amount);
        Long expenseId=null, teacherPaymentId=null;
        if (teacherId!=null) {
            TeacherPaymentRequest pay = new TeacherPaymentRequest();
            pay.setMonth(row.period()); pay.setDate(request.date()); pay.setAmount(amount); pay.setReference(clean(request.reference()));
            teacherPaymentId=teacherWork.addPayment(teacherId,pay,actor,true).id();
            jdbc.update("UPDATE teacher_payments SET managed_payment=TRUE WHERE id=?",teacherPaymentId);
        } else {
            var expense = expenses.saveAndFlush(Expense.builder().school(school)
                    .category(categories.findById(row.categoryId()).orElseThrow()).amount(amount).label(row.label())
                    .supplier(row.supplier()).expenseDate(request.date()).paymentMethod(request.method()).managedPayment(true)
                    .reference(clean(request.reference())).notes(row.notes()).createdBy(users.getReferenceById(actor)).build());
            expenseId=expense.getId();
        }
        jdbc.update("""
            INSERT INTO school_payable_payments(school_id,payable_id,request_id,payment_date,amount,method,reference,expense_id,teacher_payment_id,created_by)
            VALUES(?,?,?,?,?,?,?,?,?,?)
            """,schoolId,id,request.requestId(),request.date(),amount,request.method().name(),clean(request.reference()),expenseId,teacherPaymentId,actor);
        return find(schoolId,id,false);
    }

    public void cancel(Long schoolId, Long id, Long actor, boolean admin) {
        requireSchool(schoolId,actor,admin); var row=find(schoolId,id,true);
        if (row.paid().signum()>0 || row.source().endsWith("SALARY"))
            throw new IllegalArgumentException("Une dépense déjà payée ou un salaire ne peut pas être annulé");
        jdbc.update("UPDATE school_payables SET cancelled=TRUE WHERE id=?",id);
    }

    static void validatePayment(PayableDto.Row row, BigDecimal amount) {
        if (row.status().equals("CANCELLED")) throw new IllegalArgumentException("Cette dépense est annulée");
        if (amount.signum()<=0 || amount.compareTo(row.remaining())>0)
            throw new IllegalArgumentException("Le montant doit être positif et ne pas dépasser le reste à payer ("+row.remaining()+" FCFA)");
    }
    static String status(boolean cancelled, BigDecimal amount, BigDecimal paid) {
        return cancelled ? "CANCELLED" : amount.signum()==0 && paid.signum()==0 ? "NOT_DUE"
                : paid.compareTo(amount)>=0 ? "PAID" : paid.signum()>0 ? "PARTIAL" : "UNPAID";
    }
    static BigDecimal money(BigDecimal amount) {
        if (amount==null || amount.signum()<=0 || amount.precision()-amount.scale()>10)
            throw new IllegalArgumentException("Montant invalide");
        try { return amount.setScale(2,RoundingMode.UNNECESSARY); }
        catch (ArithmeticException ex) { throw new IllegalArgumentException("Le montant doit avoir au maximum deux décimales"); }
    }
    private PayableDto.Row row(ResultSet rs) throws SQLException {
        BigDecimal amount=rs.getBigDecimal("amount"),paid=rs.getBigDecimal("paid");
        Long teacher=(Long)rs.getObject("teacher_id");
        String period=rs.getDate("period").toLocalDate().toString().substring(0,7);
        if (teacher!=null) amount=teacherWork.getTeacherDetail(teacher,period,0L,true).month().amountDue();
        boolean cancelled=rs.getBoolean("cancelled");
        BigDecimal remaining=amount.subtract(paid).max(BigDecimal.ZERO);
        LocalDate due=rs.getDate("due_date").toLocalDate();
        return new PayableDto.Row(rs.getLong("id"),rs.getString("source"),period,due,rs.getString("label"),
                rs.getString("supplier"),rs.getLong("category_id"),rs.getString("category_name"),amount,paid,
                cancelled ? BigDecimal.ZERO : remaining,status(cancelled,amount,paid),!cancelled && remaining.signum()>0 && due.isBefore(LocalDate.now(clock)),rs.getString("notes"));
    }
    private PayableDto.Row find(Long schoolId,Long id,boolean lock) {
        // The aggregate must use a new READ COMMITTED snapshot after waiting for the lock.
        // Combining FOR UPDATE with the payment subquery can otherwise return a stale balance.
        if (lock) {
            var locked = jdbc.queryForList("SELECT id FROM school_payables WHERE school_id=? AND id=? FOR UPDATE",Long.class,schoolId,id);
            if (locked.isEmpty()) throw new IllegalArgumentException("Dépense à payer introuvable");
        }
        var rows=jdbc.query(SELECT+" WHERE p.school_id=? AND p.id=?",(rs,n)->row(rs),schoolId,id);
        if(rows.isEmpty()) throw new IllegalArgumentException("Dépense à payer introuvable");
        return rows.getFirst();
    }
    private void insert(Long school,Long category,String source,String key,YearMonth period,LocalDate due,BigDecimal amount,
                        String label,String supplier,String notes,Long teacher,Long actor) {
        jdbc.update("""
            INSERT INTO school_payables(school_id,category_id,source,source_key,period,due_date,amount,label,supplier,notes,teacher_id,created_by)
            VALUES(?,?,?,?,?,?,?,?,?,?,?,?) ON CONFLICT (school_id,source_key,period) DO NOTHING
            """,school,category,source,key,period.atDay(1),due,amount,clip(label,150),clip(supplier,150),notes,teacher,actor);
    }
    private void requireCategory(Long school,Long id) {
        categories.findById(id).filter(c -> c.getSchool().getId().equals(school) && c.isActive() && !c.isPayroll())
                .orElseThrow(() -> new IllegalArgumentException("Catégorie indisponible pour cet établissement"));
    }
    private static YearMonth month(String value) {
        try { return YearMonth.parse(value); } catch (RuntimeException ex) { throw new IllegalArgumentException("Mois invalide (AAAA-MM)"); }
    }
    private static BigDecimal sum(List<BigDecimal> values) { return values.stream().reduce(BigDecimal.ZERO,BigDecimal::add); }
    private static String clean(String value) { return value==null || value.isBlank() ? null : value.trim(); }
    private static String clip(String value,int max) { return value==null ? null : value.substring(0,Math.min(max,value.length())); }
    private static PayableDto.Payment payment(ResultSet rs) throws SQLException {
        return new PayableDto.Payment(rs.getLong("id"),rs.getDate("payment_date").toLocalDate(),rs.getBigDecimal("amount"),rs.getString("method"),rs.getString("reference"));
    }
}
