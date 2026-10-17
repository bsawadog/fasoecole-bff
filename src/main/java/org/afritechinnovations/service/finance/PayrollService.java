package org.afritechinnovations.service.finance;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.afritechinnovations.dto.finance.PayableDto;
import org.afritechinnovations.dto.finance.PayrollDto.*;
import org.afritechinnovations.model.common.*;
import org.afritechinnovations.repository.common.SchoolRepository;
import org.afritechinnovations.repository.common.SchoolStaffRepository;
import org.afritechinnovations.repository.people.TeacherRepository;
import org.afritechinnovations.security.SchoolPermissions;
import org.afritechinnovations.service.people.TeacherWorkService;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.*;
import java.util.*;

@Service
@RequiredArgsConstructor
@Transactional
public class PayrollService {
    private final SchoolRepository schools;
    private final SchoolStaffRepository staff;
    private final TeacherRepository teachers;
    private final SchoolPermissions permissions;
    private final TeacherWorkService teacherWork;
    private final SchoolPayableService payables;
    private final JdbcTemplate jdbc;
    private final ObjectMapper mapper;
    private Clock clock = Clock.systemDefaultZone();
    void setClock(Clock value) { clock = value; }
    private static final BigDecimal ZERO = BigDecimal.ZERO;

    public void importTeacherSalary(Long schoolId, Long teacherId,
            org.afritechinnovations.dto.finance.ImportTeacherSalaryRequest request, Long actor, boolean admin) {
        authorize(schoolId, actor, admin, true);
        YearMonth selected = month(request.month());
        if (selected.isAfter(YearMonth.now(clock))) throw new IllegalArgumentException("Impossible d’importer un mois futur.");
        jdbc.queryForObject("SELECT id FROM schools WHERE id=? FOR UPDATE", Long.class, schoolId);
        var teacher = teachers.findByIdForUpdate(teacherId).orElseThrow(() -> new IllegalArgumentException("Enseignant introuvable."));
        if (!schoolId.equals(teacher.getSchool().getId())) throw new AccessDeniedException("Cet enseignant appartient à un autre établissement.");
        if (Boolean.FALSE.equals(teacher.getUser().getActive()) || teacher.getHireDate() != null && teacher.getHireDate().isAfter(selected.atEndOfMonth()))
            throw new IllegalArgumentException("Cet enseignant n’est pas éligible à la paie de ce mois.");
        var before = teacherWork.getTeacherDetail(teacherId, selected.toString(), actor, true);
        // Importing a fixed monthly salary must never silently convert an hourly/attendance contract.
        if (before.rateType() != null && !"FIXED_MONTHLY".equals(before.rateType()))
            throw new IllegalArgumentException("La rémunération horaire ou selon présence se gère dans la fiche enseignant.");
        if (before.rate() == null || before.rate().compareTo(request.salary()) != 0) {
            var rate = new org.afritechinnovations.dto.people.TeacherRateRequest();
            rate.setType("FIXED_MONTHLY"); rate.setAmount(request.salary()); rate.setEffectiveFrom(selected.atDay(1));
            teacherWork.addRate(teacherId, rate, actor, true);
        }
        Long payableId = payables.prepareImportedTeacher(schoolId, teacherId, selected.toString(), actor);
        var payable = payables.salary(schoolId, payableId);
        if (request.paid() != null) {
            BigDecimal delta = request.paid().subtract(payable.paid());
            if (delta.signum() < 0) throw new IllegalArgumentException("L’import ne peut pas diminuer les salaires déjà versés.");
            if (delta.compareTo(payable.remaining()) > 0) throw new IllegalArgumentException("Le versement dépasse le salaire net restant après retenues.");
            if (delta.signum() > 0) {
                if (request.paymentDate() == null || request.paymentMethod() == null || request.paymentDate().isAfter(LocalDate.now(clock)))
                    throw new IllegalArgumentException("Renseignez la date et le mode de paiement.");
                UUID key = UUID.nameUUIDFromBytes(("salary-import:" + schoolId + ":" + teacherId + ":" + selected + ":" + request.paid().stripTrailingZeros().toPlainString()).getBytes(java.nio.charset.StandardCharsets.UTF_8));
                payables.paySalary(schoolId, payableId, new PayableDto.Pay(key, delta, request.paymentDate(), request.paymentMethod(), "Reprise par import"), actor);
            }
        }
    }

    private School authorize(Long schoolId, Long actor, boolean admin, boolean write) {
        School school = schools.findById(schoolId).orElseThrow(() -> new IllegalArgumentException("Établissement introuvable."));
        if (!admin && (school.getOwner() == null || !school.getOwner().getId().equals(actor))
                && !permissions.staffAllows(schoolId,actor,StaffModule.PAYROLL))
            throw new AccessDeniedException("L’accès aux salaires de cet établissement n’est pas autorisé.");
        if (write && school.getStatus() != SchoolStatus.ACTIVE)
            throw new IllegalArgumentException("L’établissement doit être actif pour gérer les salaires.");
        return school;
    }

    @Transactional(readOnly=true,isolation=org.springframework.transaction.annotation.Isolation.REPEATABLE_READ)
    public Overview overview(Long schoolId, String month, Long actor, boolean admin) {
        School school = authorize(schoolId,actor,admin,false);
        YearMonth selected = month(month);
        var employees = employees(school,selected,null,false);
        return new Overview(info(school),selected.toString(),employees,
                total(employees.stream().map(Employee::due).toList()),total(employees.stream().map(Employee::paid).toList()),
                total(employees.stream().map(Employee::remaining).toList()));
    }

    public Overview prepare(Long schoolId, String month, Long actor, boolean admin) {
        authorize(schoolId,actor,admin,true);
        YearMonth selected = month(month);
        if (selected.isAfter(YearMonth.now(clock))) throw new IllegalArgumentException("La préparation d’un mois futur n’est pas disponible.");
        payables.prepareSalaries(schoolId,selected.toString(),actor);
        return overview(schoolId,selected.toString(),actor,admin);
    }

    public PayableDto.Row pay(Long schoolId, Long payableId, PayableDto.Pay request, Long actor, boolean admin) {
        authorize(schoolId,actor,admin,true);
        return payables.paySalary(schoolId,payableId,request,actor);
    }

    /** Manual amounts only, per prepared remuneration/month; freeze once any money was paid. */
    public void taxes(Long schoolId, Long payableId, TaxInput input, Long actor, boolean admin) {
        authorize(schoolId,actor,admin,true);
        var ids = jdbc.queryForList("SELECT teacher_id FROM school_payables WHERE school_id=? AND id=? AND source IN ('STAFF_SALARY','TEACHER_SALARY')",schoolId,payableId);
        if (ids.isEmpty()) throw new IllegalArgumentException("Salaire préparé introuvable.");
        Long teacherId = (Long) ids.getFirst().get("teacher_id");
        if (teacherId != null) teachers.findByIdForUpdate(teacherId).orElseThrow();
        var rows = jdbc.queryForList("SELECT payroll_iuts,payroll_tpa,payroll_tax_version FROM school_payables WHERE school_id=? AND id=? FOR UPDATE",schoolId,payableId);
        if (rows.isEmpty()) throw new IllegalArgumentException("Salaire préparé introuvable.");
        var previous = rows.getFirst();
        BigDecimal iuts = taxAmount(input.iuts()), tpa = taxAmount(input.tpa());
        if (input.reason() == null || input.reason().isBlank() || input.reason().trim().length() > 300)
            throw new IllegalArgumentException("Renseignez le motif de cette saisie (300 caractères maximum).");
        // Repeating the identical assignment after a lost response is harmless.
        if (((BigDecimal)previous.get("payroll_iuts")).compareTo(iuts)==0 && ((BigDecimal)previous.get("payroll_tpa")).compareTo(tpa)==0) return;
        if (((Number)previous.get("payroll_tax_version")).longValue() != input.version())
            throw new IllegalArgumentException("Les montants ont changé. Actualisez la fiche avant de réessayer.");
        var salary = payables.salary(schoolId,payableId);
        if (salary.paid().signum() > 0)
            throw new IllegalArgumentException("Les retenues sont verrouillées après le premier versement de cette rémunération.");
        BigDecimal gross = salary.amount().add((BigDecimal)previous.get("payroll_iuts"));
        if (iuts.compareTo(gross) > 0) throw new IllegalArgumentException("L’IUTS ne peut pas dépasser la rémunération du mois.");
        jdbc.update("UPDATE school_payables SET payroll_iuts=?,payroll_tpa=?,payroll_tax_version=payroll_tax_version+1 WHERE school_id=? AND id=?",iuts,tpa,schoolId,payableId);
        jdbc.update("INSERT INTO school_payroll_tax_events(school_id,payable_id,iuts,tpa,version,reason,created_by) VALUES(?,?,?,?,?,?,?)",
                schoolId,payableId,iuts,tpa,input.version()+1,input.reason().trim(),actor);
    }

    private static BigDecimal taxAmount(BigDecimal value) {
        if (value == null || value.signum() < 0 || value.precision()-value.scale()>10)
            throw new IllegalArgumentException("Montant de prélèvement invalide.");
        try { return value.setScale(2,java.math.RoundingMode.UNNECESSARY); }
        catch (ArithmeticException error) { throw new IllegalArgumentException("Deux décimales maximum pour les prélèvements."); }
    }

    @Transactional(readOnly=true,isolation=org.springframework.transaction.annotation.Isolation.REPEATABLE_READ)
    public EmployeeDetail detail(Long schoolId, Long userId, String month, Long actor, boolean admin) {
        School school = authorize(schoolId,actor,admin,false);
        YearMonth selected = month(month);
        Employee employee = employee(school,userId,selected,true);
        List<LocalDate> months = jdbc.query("""
            SELECT period FROM (
              SELECT p.period FROM school_payables p WHERE p.school_id=? AND p.source IN ('STAFF_SALARY','TEACHER_SALARY')
                AND (p.source_key IN (SELECT 'staff:'||s.id FROM school_staff s WHERE s.school_id=? AND s.user_id=?)
                  OR p.teacher_id IN (SELECT t.id FROM teachers t WHERE t.school_id=? AND t.user_id=?))
              UNION SELECT p.pay_month FROM teacher_payments p JOIN teachers t ON t.id=p.teacher_id WHERE t.school_id=? AND t.user_id=?
            ) recorded ORDER BY period DESC LIMIT 24
            """, (rs,n) -> rs.getDate(1).toLocalDate(),schoolId,schoolId,userId,schoolId,userId,schoolId,userId);
        var history = months.stream().map(date -> {
            Employee past = employee(school,userId,YearMonth.from(date),false);
            return new MonthHistory(YearMonth.from(date).toString(),past.due(),past.paid(),past.remaining(),past.status(),
                    total(past.contracts().stream().map(Contract::iuts).toList()),total(past.contracts().stream().map(Contract::net).toList()),total(past.contracts().stream().map(Contract::tpa).toList()));
        }).toList();
        var documents = jdbc.query("SELECT id,kind,period,created_at FROM school_payroll_documents WHERE school_id=? AND employee_user_id=? ORDER BY id DESC LIMIT 50",
                (rs,n) -> new DocumentInfo(rs.getLong("id"),reference(schoolId,rs.getLong("id"),DocumentKind.valueOf(rs.getString("kind")),rs.getTimestamp("created_at").toLocalDateTime()),
                        DocumentKind.valueOf(rs.getString("kind")),rs.getDate("period") == null ? null : YearMonth.from(rs.getDate("period").toLocalDate()).toString(),rs.getTimestamp("created_at").toLocalDateTime()),schoolId,userId);
        return new EmployeeDetail(info(school),selected.toString(),employee,history,documents);
    }

    public Document issue(Long schoolId, DocumentInput request, Long actor, boolean admin) {
        School school = authorize(schoolId,actor,admin,true);
        LocalDate period = request.kind() == DocumentKind.PAYSLIP ? month(request.month()).atDay(1) : null;
        if (period != null && YearMonth.from(period).isAfter(YearMonth.now(clock)))
            throw new IllegalArgumentException("Une fiche de paie ne peut pas être éditée pour un mois futur.");
        // Serialize retries of the same edition, including concurrent browser submissions.
        // A dedicated lock avoids blocking the school's foreign keys while waiting for salary locks.
        jdbc.queryForList("SELECT pg_advisory_xact_lock(hashtextextended(?,0))","payroll-document:"+schoolId);
        var previous = jdbc.queryForList("SELECT id,employee_user_id,kind,period FROM school_payroll_documents WHERE school_id=? AND request_id=?",schoolId,request.requestId());
        if (!previous.isEmpty()) {
            var row = previous.getFirst();
            LocalDate previousPeriod = row.get("period") == null ? null : ((java.sql.Date)row.get("period")).toLocalDate();
            if (!request.userId().equals(row.get("employee_user_id")) || !request.kind().name().equals(row.get("kind")) || !Objects.equals(period,previousPeriod))
                throw new IllegalArgumentException("Cette référence correspond déjà à un autre document.");
            return readDocument(schoolId,((Number)row.get("id")).longValue());
        }
        YearMonth selected = period == null ? YearMonth.now(clock) : YearMonth.from(period);
        if (period != null) {
            teachers.findBySchoolId(schoolId).stream().filter(t -> t.getUser().getId().equals(request.userId()))
                    .sorted(Comparator.comparing(org.afritechinnovations.model.people.Teacher::getId))
                    .forEach(t -> teachers.findByIdForUpdate(t.getId()).orElseThrow());
            jdbc.queryForList("""
                SELECT p.id FROM school_payables p WHERE p.school_id=? AND p.period=? AND p.source IN ('STAFF_SALARY','TEACHER_SALARY')
                AND (p.source_key IN (SELECT 'staff:'||s.id FROM school_staff s WHERE s.school_id=? AND s.user_id=?)
                  OR p.teacher_id IN (SELECT t.id FROM teachers t WHERE t.school_id=? AND t.user_id=?)) ORDER BY p.id FOR UPDATE
                """,Long.class,schoolId,period,schoolId,request.userId(),schoolId,request.userId());
        }
        Employee employee = employee(school,request.userId(),selected,true);
        if (request.kind() == DocumentKind.PAYSLIP && employee.contracts().stream().noneMatch(c -> c.prepared() || c.paid().signum() > 0))
            throw new IllegalArgumentException("Préparez d’abord les salaires de ce mois avant d’éditer une fiche de paie.");
        if (request.kind() == DocumentKind.PAYSLIP && employee.contracts().stream().anyMatch(c -> c.eligible() && c.rate() != null && c.rate().signum() > 0
                && !c.prepared() && !(c.kind().equals("TEACHER") && c.paid().signum() > 0)))
            throw new IllegalArgumentException("Préparez toutes les rémunérations de cet employé pour le mois avant d’éditer sa fiche.");
        if (request.kind() == DocumentKind.EMPLOYMENT_CERTIFICATE) employee = certificateEmployee(employee);
        Snapshot snapshot = new Snapshot(info(school),period == null ? null : selected.toString(),employee,LocalDate.now(clock));
        try {
            Long id = jdbc.queryForObject("""
                INSERT INTO school_payroll_documents(school_id,employee_user_id,kind,period,request_id,snapshot,created_by)
                VALUES(?,?,?,?,?,?::jsonb,?) RETURNING id
                """,Long.class,schoolId,request.userId(),request.kind().name(),period,request.requestId(),mapper.writeValueAsString(snapshot),actor);
            return readDocument(schoolId,id);
        } catch (JsonProcessingException error) { throw new IllegalStateException("Impossible de conserver le document de paie.",error); }
    }

    @Transactional(readOnly=true)
    public Document document(Long schoolId, Long id, Long actor, boolean admin) {
        authorize(schoolId,actor,admin,false); return readDocument(schoolId,id);
    }
    private Document readDocument(Long schoolId, Long id) {
        var rows = jdbc.query("SELECT * FROM school_payroll_documents WHERE school_id=? AND id=?",(rs,n) -> documentRow(schoolId,rs),schoolId,id);
        return rows.stream().findFirst().orElseThrow(() -> new IllegalArgumentException("Document introuvable dans cet établissement."));
    }
    private Document documentRow(Long schoolId, ResultSet rs) throws SQLException {
        try {
            Long id = rs.getLong("id"); DocumentKind kind = DocumentKind.valueOf(rs.getString("kind"));
            LocalDateTime date = rs.getTimestamp("created_at").toLocalDateTime();
            return new Document(id,reference(schoolId,id,kind,date),kind,rs.getDate("period") == null ? null : YearMonth.from(rs.getDate("period").toLocalDate()).toString(),date,
                    mapper.readValue(rs.getString("snapshot"),Snapshot.class));
        } catch (JsonProcessingException error) { throw new IllegalStateException("Document conservé illisible.",error); }
    }

    private Employee employee(School school, Long userId, YearMonth selected, boolean payments) {
        return employees(school,selected,userId,payments).stream().findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Cet employé n’appartient pas à l’établissement."));
    }
    private static final class Person {
        final User user; final List<Contract> contracts = new ArrayList<>(); final Set<String> subjects = new TreeSet<>();
        Person(User user) { this.user = user; }
    }
    private List<Employee> employees(School school, YearMonth selected, Long onlyUser, boolean includePayments) {
        Long schoolId = school.getId();
        Map<String,Long> prepared = new HashMap<>();
        Map<Long,Map<String,Object>> taxes = new HashMap<>();
        jdbc.queryForList("SELECT id,payroll_iuts,payroll_tpa,payroll_tax_version FROM school_payables WHERE school_id=? AND period=? AND source IN ('STAFF_SALARY','TEACHER_SALARY')",schoolId,selected.atDay(1))
                .forEach(row -> taxes.put(((Number)row.get("id")).longValue(),row));
        jdbc.query("SELECT source_key,id FROM school_payables WHERE school_id=? AND period=? AND source IN ('STAFF_SALARY','TEACHER_SALARY')",
                (rs,n) -> { prepared.put(rs.getString(1),rs.getLong(2)); return rs.getLong(2); },schoolId,selected.atDay(1));
        Map<Long,Person> people = new LinkedHashMap<>();
        for (SchoolStaff member : staff.findBySchoolWithUser(schoolId)) {
            User user = member.getUser(); if (onlyUser != null && !onlyUser.equals(user.getId())) continue;
            String key = "staff:" + member.getId(); Long payableId = prepared.get(key);
            PayableDto.Row row = payableId == null ? null : payables.salary(schoolId,payableId);
            boolean eligible = member.getCreatedAt() == null || !member.getCreatedAt().toLocalDate().isAfter(selected.atEndOfMonth());
            BigDecimal iuts = tax(taxes,payableId,"payroll_iuts"), tpa = tax(taxes,payableId,"payroll_tpa");
            BigDecimal due = row != null ? row.amount().add(iuts) : eligible && member.getMonthlySalary() != null ? member.getMonthlySalary() : ZERO;
            BigDecimal paid = row == null ? ZERO : row.paid();
            Person person = people.computeIfAbsent(user.getId(),id -> new Person(user));
            person.contracts.add(new Contract(key,"STAFF",member.getJobTitle(),null,member.isActive() && !Boolean.FALSE.equals(user.getActive()),
                    null,"FIXED_MONTHLY",row != null ? due : member.getMonthlySalary(),eligible,row != null,payableId,due,paid,due.subtract(iuts).subtract(paid).max(ZERO),ZERO,ZERO,
                    includePayments && row != null ? payables.payments(schoolId,payableId,0L,true) : List.of(),iuts,tpa,due.subtract(iuts).max(ZERO),taxVersion(taxes,payableId)));
        }
        for (var teacher : teachers.findBySchoolId(schoolId)) {
            User user = teacher.getUser(); if (onlyUser != null && !onlyUser.equals(user.getId())) continue;
            String key = "teacher:" + teacher.getId(); Long payableId = prepared.get(key);
            var detail = teacherWork.getTeacherDetail(teacher.getId(),selected.toString(),0L,true);
            boolean eligible = teacher.getHireDate() == null || !teacher.getHireDate().isAfter(selected.atEndOfMonth());
            BigDecimal due = eligible ? detail.month().amountDue() : ZERO, paid = detail.month().paid();
            BigDecimal iuts = tax(taxes,payableId,"payroll_iuts"), tpa = tax(taxes,payableId,"payroll_tpa");
            Person person = people.computeIfAbsent(user.getId(),id -> new Person(user));
            person.subjects.addAll(detail.teacher().subjects());
            teacher.getTeachingSubjects().forEach(subject -> person.subjects.add(subject.getName()));
            List<PayableDto.Payment> payments = includePayments ? jdbc.query("""
                SELECT t.id,t.payment_date,t.amount,p.method,t.reference FROM teacher_payments t
                LEFT JOIN school_payable_payments p ON p.teacher_payment_id=t.id
                WHERE t.teacher_id=? AND t.pay_month=? ORDER BY t.payment_date,t.id
                """, (rs,n) -> new PayableDto.Payment(rs.getLong("id"),rs.getDate("payment_date").toLocalDate(),rs.getBigDecimal("amount"),rs.getString("method"),rs.getString("reference")),teacher.getId(),selected.atDay(1)) : List.of();
            person.contracts.add(new Contract(key,"TEACHER","Enseignant",teacher.getEmployeeNumber(),!Boolean.FALSE.equals(user.getActive()),teacher.getHireDate(),
                    detail.rateType(),detail.rate(),eligible,payableId != null,payableId,due,paid,due.subtract(iuts).subtract(paid).max(ZERO),detail.month().workedHours(),detail.month().extraHours(),payments,
                    iuts,tpa,due.subtract(iuts).max(ZERO),taxVersion(taxes,payableId)));
        }
        return people.values().stream().map(person -> {
            BigDecimal due = total(person.contracts.stream().map(Contract::due).toList()), paid = total(person.contracts.stream().map(Contract::paid).toList());
            BigDecimal remaining = total(person.contracts.stream().map(Contract::remaining).toList());
            return new Employee(person.user.getId(),person.user.getFirstName(),person.user.getLastName(),person.user.getEmail(),person.user.getPhone(),
                    person.contracts.stream().anyMatch(Contract::active),List.copyOf(person.subjects),List.copyOf(person.contracts),due,paid,remaining,
                    status(person.contracts,total(person.contracts.stream().map(Contract::net).toList()),paid,remaining));
        }).sorted(Comparator.comparing(Employee::lastName,String.CASE_INSENSITIVE_ORDER).thenComparing(Employee::firstName,String.CASE_INSENSITIVE_ORDER)).toList();
    }

    private Employee certificateEmployee(Employee employee) {
        var contracts = employee.contracts().stream().filter(c -> c.active() && (c.hireDate() == null || !c.hireDate().isAfter(LocalDate.now(clock))))
                .map(c -> new Contract(c.key(),c.kind(),c.jobTitle(),c.employeeNumber(),true,c.hireDate(),null,null,true,false,null,ZERO,ZERO,ZERO,ZERO,ZERO,List.of(),ZERO,ZERO,ZERO,0)).toList();
        if (contracts.isEmpty()) throw new IllegalArgumentException("Une attestation de travail est disponible pour un employé actif ayant commencé son emploi.");
        return new Employee(employee.userId(),employee.firstName(),employee.lastName(),employee.email(),employee.phone(),true,
                contracts.stream().anyMatch(c -> c.kind().equals("TEACHER")) ? employee.subjects() : List.of(),contracts,ZERO,ZERO,ZERO,"");
    }
    static String status(List<Contract> contracts, BigDecimal due, BigDecimal paid, BigDecimal remaining) {
        if (paid.compareTo(due) > 0) return "OVERPAID";
        if (paid.signum() > 0) return remaining.signum() == 0 ? "PAID" : "PARTIAL";
        if (contracts.stream().noneMatch(Contract::eligible)) return "NOT_ELIGIBLE";
        if (contracts.stream().allMatch(c -> c.rate() == null || c.rate().signum() <= 0)) return "UNCONFIGURED";
        if (contracts.stream().anyMatch(c -> c.eligible() && c.rate() != null && c.rate().signum() > 0 && !c.prepared())) return "UNPREPARED";
        return due.signum() == 0 ? "NOT_DUE" : "UNPAID";
    }
    private static BigDecimal tax(Map<Long,Map<String,Object>> rows,Long id,String column) {
        return id == null || !rows.containsKey(id) ? ZERO : (BigDecimal) rows.get(id).get(column);
    }
    private static long taxVersion(Map<Long,Map<String,Object>> rows,Long id) {
        return id == null || !rows.containsKey(id) ? 0 : ((Number) rows.get(id).get("payroll_tax_version")).longValue();
    }
    private static SchoolInfo info(School school) { return new SchoolInfo(school.getId(),school.getName(),school.getAddress(),school.getPhone(),school.getEmail()); }
    private static BigDecimal total(List<BigDecimal> amounts) { return amounts.stream().reduce(ZERO,BigDecimal::add); }
    private static YearMonth month(String value) {
        try { YearMonth month = YearMonth.parse(value); if (month.getYear() < 1900 || month.getYear() > 2200) throw new IllegalArgumentException(); return month; }
        catch (RuntimeException error) { throw new IllegalArgumentException("Choisissez un mois valide au format AAAA-MM."); }
    }
    private static String reference(Long school, Long id, DocumentKind kind, LocalDateTime date) {
        return (kind == DocumentKind.PAYSLIP ? "PAIE" : "ATT") + "-" + school + "-" + date.toLocalDate() + "-" + id;
    }
}
