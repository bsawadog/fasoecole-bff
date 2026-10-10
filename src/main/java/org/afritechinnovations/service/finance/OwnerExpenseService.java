package org.afritechinnovations.service.finance;

import lombok.RequiredArgsConstructor;
import org.afritechinnovations.dto.finance.OwnerExpenseDto;
import org.afritechinnovations.model.academic.AcademicYear;
import org.afritechinnovations.model.common.School;
import org.afritechinnovations.model.common.User;
import org.afritechinnovations.model.finance.Expense;
import org.afritechinnovations.model.finance.ExpenseBudget;
import org.afritechinnovations.model.finance.ExpenseCategory;
import org.afritechinnovations.model.people.TeacherPayment;
import org.afritechinnovations.repository.academic.AcademicYearRepository;
import org.afritechinnovations.repository.common.SchoolRepository;
import org.afritechinnovations.repository.common.UserRepository;
import org.afritechinnovations.repository.finance.ExpenseBudgetRepository;
import org.afritechinnovations.repository.finance.ExpenseCategoryRepository;
import org.afritechinnovations.repository.finance.ExpenseRepository;
import org.afritechinnovations.repository.finance.PaymentRepository;
import org.afritechinnovations.repository.people.TeacherPaymentRepository;
import org.afritechinnovations.model.common.StaffModule;
import org.afritechinnovations.security.SchoolPermissions;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.format.TextStyle;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Stream;

/** Dépenses, budget annuel par catégorie et bilan recettes / dépenses d'un établissement. */
@Service
@RequiredArgsConstructor
@Transactional
public class OwnerExpenseService {

    public static final String SOURCE_MANUAL = "MANUAL";
    public static final String SOURCE_PAYROLL = "PAYROLL";

    /** Catégories proposées à la première ouverture du module : {nom, description, code système}. */
    private static final List<String[]> DEFAULT_CATEGORIES = List.of(
            new String[]{"Salaires des enseignants", "Alimentée automatiquement par la paie des enseignants",
                    ExpenseCategory.PAYROLL},
            new String[]{"Salaires du personnel", "Direction, secrétariat, gardiennage, entretien", "STAFF_PAYROLL"},
            new String[]{"Loyer & locaux", "Loyer, aménagement des salles", null},
            new String[]{"Eau & électricité", "Factures ONEA, SONABEL, carburant groupe électrogène", null},
            new String[]{"Fournitures & matériel pédagogique", "Craie, cahiers, manuels, matériel informatique", null},
            new String[]{"Entretien & réparations", "Bâtiments, mobilier, équipements", null},
            new String[]{"Transport & carburant", "Déplacements, véhicules, carburant", null},
            new String[]{"Communication & internet", "Téléphone, internet, SMS", null},
            new String[]{"Impôts & taxes", "Patente, taxes, cotisations", null},
            new String[]{"Divers", "Autres dépenses", null});

    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);
    private final SchoolPermissions permissions;

    private final SchoolRepository schoolRepository;
    private final ExpenseCategoryRepository categoryRepository;
    private final ExpenseRepository expenseRepository;
    private final ExpenseBudgetRepository budgetRepository;
    private final AcademicYearRepository academicYearRepository;
    private final PaymentRepository paymentRepository;
    private final TeacherPaymentRepository teacherPaymentRepository;
    private final UserRepository userRepository;

    private Clock clock = Clock.systemDefaultZone();

    void setClock(Clock clock) {
        this.clock = clock;
    }

    // ---------------------------------------------------------------- catégories

    public List<OwnerExpenseDto.CategoryInfo> listCategories(Long schoolId, Long ownerId, boolean systemAdmin) {
        School school = requireOwnedSchool(schoolId, ownerId, systemAdmin);
        Map<Long, Long> counts = new HashMap<>();
        for (Object[] row : expenseRepository.countByCategoryForSchool(schoolId)) {
            counts.put((Long) row[0], ((Number) row[1]).longValue());
        }
        return categoriesOf(school).stream()
                .map(c -> toCategoryInfo(c, counts.getOrDefault(c.getId(), 0L)))
                .toList();
    }

    public OwnerExpenseDto.CategoryInfo createCategory(Long schoolId, OwnerExpenseDto.CategoryRequest request,
                                                       Long ownerId, boolean systemAdmin) {
        School school = requireOwnedSchool(schoolId, ownerId, systemAdmin);
        categoriesOf(school);
        String name = request.name().trim();
        if (categoryRepository.existsBySchoolIdAndNameIgnoreCase(schoolId, name)) {
            throw new IllegalArgumentException("Une catégorie porte déjà ce nom");
        }
        ExpenseCategory category = ExpenseCategory.builder()
                .school(school).name(name).description(blankToNull(request.description())).build();
        return toCategoryInfo(categoryRepository.save(category), 0);
    }

    public OwnerExpenseDto.CategoryInfo updateCategory(Long categoryId, OwnerExpenseDto.CategoryRequest request,
                                                       Long ownerId, boolean systemAdmin) {
        ExpenseCategory category = requireOwnedCategory(categoryId, ownerId, systemAdmin);
        String name = request.name().trim();
        if (categoryRepository.existsBySchoolIdAndNameIgnoreCaseAndIdNot(category.getSchool().getId(), name, categoryId)) {
            throw new IllegalArgumentException("Une catégorie porte déjà ce nom");
        }
        if (Boolean.FALSE.equals(request.active()) && category.isPayroll()) {
            throw new IllegalArgumentException("La catégorie de la paie des enseignants ne peut pas être archivée");
        }
        category.setName(name);
        category.setDescription(blankToNull(request.description()));
        if (request.active() != null) {
            category.setActive(request.active());
        }
        boolean used = expenseRepository.existsByCategoryId(categoryId);
        return toCategoryInfo(categoryRepository.save(category), used ? 1 : 0);
    }

    /** Supprime une catégorie jamais utilisée ; sinon l'archive pour conserver l'historique. */
    public boolean deleteCategory(Long categoryId, Long ownerId, boolean systemAdmin) {
        ExpenseCategory category = requireOwnedCategory(categoryId, ownerId, systemAdmin);
        if (category.isPayroll() || "STAFF_PAYROLL".equals(category.getSystemCode())) {
            throw new IllegalArgumentException("Les catégories des salaires ne peuvent pas être supprimées");
        }
        if (expenseRepository.existsByCategoryId(categoryId) || categoryRepository.isUsedByPayables(categoryId)) {
            category.setActive(false);
            categoryRepository.save(category);
            return false;
        }
        categoryRepository.delete(category);
        return true;
    }

    // ---------------------------------------------------------------- dépenses

    public List<OwnerExpenseDto.ExpenseRow> listExpenses(Long schoolId, LocalDate from, LocalDate to, Long categoryId,
                                                         Long ownerId, boolean systemAdmin) {
        School school = requireOwnedSchool(schoolId, ownerId, systemAdmin);
        LocalDate today = LocalDate.now(clock);
        LocalDate start = from != null ? from : today.withDayOfMonth(1);
        LocalDate end = to != null ? to : today;
        if (start.isAfter(end)) {
            throw new IllegalArgumentException("La date de début doit précéder la date de fin");
        }
        ExpenseCategory payroll = payrollCategory(categoriesOf(school));
        List<OwnerExpenseDto.ExpenseRow> rows = new ArrayList<>(expenseRepository.findForSchoolBetween(schoolId, start, end)
                .stream()
                .filter(e -> categoryId == null || e.getCategory().getId().equals(categoryId))
                .map(OwnerExpenseService::toRow)
                .toList());
        if (payroll != null && (categoryId == null || categoryId.equals(payroll.getId()))) {
            teacherPaymentRepository.findForSchoolBetween(schoolId, start, end)
                    .forEach(tp -> rows.add(toRow(tp, payroll)));
        }
        rows.sort(Comparator.comparing(OwnerExpenseDto.ExpenseRow::expenseDate).reversed()
                .thenComparing(OwnerExpenseDto.ExpenseRow::id, Comparator.reverseOrder()));
        return rows;
    }

    public OwnerExpenseDto.ExpenseRow createExpense(Long schoolId, OwnerExpenseDto.ExpenseRequest request,
                                                    Long ownerId, boolean systemAdmin) {
        School school = requireOwnedSchool(schoolId, ownerId, systemAdmin);
        Expense expense = Expense.builder()
                .school(school)
                .createdBy(userRepository.findById(ownerId).orElse(null))
                .build();
        apply(expense, request, schoolId, true);
        return toRow(expenseRepository.save(expense));
    }

    public OwnerExpenseDto.ExpenseRow updateExpense(Long expenseId, OwnerExpenseDto.ExpenseRequest request,
                                                    Long ownerId, boolean systemAdmin) {
        Expense expense = requireOwnedExpense(expenseId, ownerId, systemAdmin);
        if (expense.isManagedPayment()) throw new IllegalArgumentException("Ce paiement est lié à une dépense à payer et ne peut pas être modifié ici");
        boolean categoryChanged = !expense.getCategory().getId().equals(request.categoryId());
        apply(expense, request, expense.getSchool().getId(), categoryChanged);
        expense.setUpdatedAt(LocalDateTime.now(clock));
        return toRow(expenseRepository.save(expense));
    }

    public void deleteExpense(Long expenseId, Long ownerId, boolean systemAdmin) {
        Expense expense = requireOwnedExpense(expenseId, ownerId, systemAdmin);
        if (expense.isManagedPayment()) throw new IllegalArgumentException("Ce paiement est lié à une dépense à payer et ne peut pas être supprimé");
        expenseRepository.delete(expense);
    }

    public String exportExpensesCsv(Long schoolId, LocalDate from, LocalDate to, Long categoryId,
                                    Long ownerId, boolean systemAdmin) {
        StringBuilder csv = new StringBuilder("\uFEFFDate;Catégorie;Libellé;Bénéficiaire;Mode;Référence;Montant (FCFA);Origine\n");
        for (OwnerExpenseDto.ExpenseRow row : listExpenses(schoolId, from, to, categoryId, ownerId, systemAdmin)) {
            csv.append(row.expenseDate()).append(';')
                    .append(csvCell(row.categoryName())).append(';')
                    .append(csvCell(row.label())).append(';')
                    .append(csvCell(row.supplier())).append(';')
                    .append(methodLabel(row.method())).append(';')
                    .append(csvCell(row.reference())).append(';')
                    .append(row.amount().setScale(0, RoundingMode.HALF_UP).toPlainString()).append(';')
                    .append(SOURCE_PAYROLL.equals(row.source()) ? "Paie enseignants" : "Saisie").append('\n');
        }
        return csv.toString();
    }

    // ---------------------------------------------------------------- bilan & budget

    public OwnerExpenseDto.Summary summary(Long schoolId, Long academicYearId, Long ownerId, boolean systemAdmin) {
        School school = requireOwnedSchool(schoolId, ownerId, systemAdmin);
        List<ExpenseCategory> categories = categoriesOf(school);
        List<AcademicYear> years = academicYearRepository.findBySchoolId(schoolId).stream()
                .sorted(Comparator.comparing(AcademicYear::getStartDate).reversed())
                .toList();
        Long selected = org.afritechinnovations.service.academic.SelectedAcademicYear.id(schoolId);
        AcademicYear year = selectYear(years, selected != null ? selected : academicYearId);
        LocalDate today = LocalDate.now(clock);
        LocalDate from = year != null ? year.getStartDate() : today.withDayOfYear(1);
        LocalDate to = year != null ? year.getEndDate() : today.withDayOfYear(today.lengthOfYear());

        Map<YearMonth, BigDecimal> incomeByMonth = new HashMap<>();
        for (Object[] row : paymentRepository.findAmountsForSchoolBetween(schoolId, from, to)) {
            incomeByMonth.merge(YearMonth.from((LocalDate) row[0]), (BigDecimal) row[1], BigDecimal::add);
        }
        ExpenseCategory payroll = payrollCategory(categories);
        List<Expense> manual = expenseRepository.findForSchoolBetween(schoolId, from, to);
        List<TeacherPayment> payrollPayments = teacherPaymentRepository.findForSchoolBetween(schoolId, from, to);

        Map<YearMonth, BigDecimal> expensesByMonth = new HashMap<>();
        Map<Long, BigDecimal> spentByCategory = new HashMap<>();
        for (Expense e : manual) {
            expensesByMonth.merge(YearMonth.from(e.getExpenseDate()), e.getAmount(), BigDecimal::add);
            spentByCategory.merge(e.getCategory().getId(), e.getAmount(), BigDecimal::add);
        }
        for (TeacherPayment tp : payrollPayments) {
            expensesByMonth.merge(YearMonth.from(tp.getPaymentDate()), tp.getAmount(), BigDecimal::add);
            if (payroll != null) {
                spentByCategory.merge(payroll.getId(), tp.getAmount(), BigDecimal::add);
            }
        }

        List<OwnerExpenseDto.MonthLine> months = new ArrayList<>();
        for (YearMonth m = YearMonth.from(from); !m.isAfter(YearMonth.from(to)); m = m.plusMonths(1)) {
            BigDecimal income = incomeByMonth.getOrDefault(m, BigDecimal.ZERO);
            BigDecimal spent = expensesByMonth.getOrDefault(m, BigDecimal.ZERO);
            months.add(new OwnerExpenseDto.MonthLine(m.toString(), income, spent, income.subtract(spent)));
        }

        Map<Long, BigDecimal> budgets = new HashMap<>();
        if (year != null) {
            budgetRepository.findByAcademicYearId(year.getId())
                    .forEach(b -> budgets.put(b.getCategory().getId(), b.getAmount()));
        }
        List<OwnerExpenseDto.CategoryLine> lines = categories.stream()
                .map(c -> categoryLine(c, budgets.getOrDefault(c.getId(), BigDecimal.ZERO),
                        spentByCategory.getOrDefault(c.getId(), BigDecimal.ZERO)))
                .filter(l -> l.active() || l.spent().signum() > 0 || l.budget().signum() > 0)
                .sorted(Comparator.comparing(OwnerExpenseDto.CategoryLine::spent).reversed()
                        .thenComparing(OwnerExpenseDto.CategoryLine::name, String.CASE_INSENSITIVE_ORDER))
                .toList();

        BigDecimal income = sum(incomeByMonth.values().stream());
        BigDecimal expenses = sum(expensesByMonth.values().stream());
        BigDecimal budgetTotal = sum(budgets.values().stream());

        YearMonth current = YearMonth.from(today);
        BigDecimal spentThisMonth = sum(Stream.concat(
                expenseRepository.findForSchoolBetween(schoolId, current.atDay(1), current.atEndOfMonth()).stream()
                        .map(Expense::getAmount),
                teacherPaymentRepository.findForSchoolBetween(schoolId, current.atDay(1), current.atEndOfMonth()).stream()
                        .map(TeacherPayment::getAmount)));

        List<OwnerExpenseDto.ExpenseRow> recent = Stream.concat(
                        manual.stream().map(OwnerExpenseService::toRow),
                        payroll == null ? Stream.empty() : payrollPayments.stream().map(tp -> toRow(tp, payroll)))
                .sorted(Comparator.comparing(OwnerExpenseDto.ExpenseRow::expenseDate).reversed()
                        .thenComparing(OwnerExpenseDto.ExpenseRow::id, Comparator.reverseOrder()))
                .limit(6)
                .toList();

        return new OwnerExpenseDto.Summary(
                school.getName(),
                year == null ? null : toYearInfo(year),
                years.stream().map(OwnerExpenseService::toYearInfo).toList(),
                from, to, income, expenses, income.subtract(expenses), spentThisMonth,
                budgetTotal, rate(expenses, budgetTotal),
                lines.stream().filter(OwnerExpenseDto.CategoryLine::overBudget).count(),
                months, lines, recent);
    }

    public OwnerExpenseDto.Summary saveBudget(Long schoolId, OwnerExpenseDto.BudgetRequest request,
                                              Long ownerId, boolean systemAdmin) {
        School school = requireOwnedSchool(schoolId, ownerId, systemAdmin);
        AcademicYear year = academicYearRepository.findById(request.academicYearId())
                .orElseThrow(() -> new IllegalArgumentException("Année scolaire introuvable"));
        if (!year.getSchool().getId().equals(schoolId)) {
            throw new AccessDeniedException("Cette année scolaire n'appartient pas à cet établissement");
        }
        Map<Long, ExpenseCategory> categories = new HashMap<>();
        categoriesOf(school).forEach(c -> categories.put(c.getId(), c));
        Map<Long, ExpenseBudget> existing = new LinkedHashMap<>();
        budgetRepository.findByAcademicYearId(year.getId()).forEach(b -> existing.put(b.getCategory().getId(), b));

        for (OwnerExpenseDto.BudgetLine line : request.lines()) {
            ExpenseCategory category = categories.get(line.categoryId());
            if (category == null) {
                throw new IllegalArgumentException("Catégorie inconnue pour cet établissement : " + line.categoryId());
            }
            BigDecimal amount = line.amount().setScale(2, RoundingMode.HALF_UP);
            ExpenseBudget budget = existing.get(category.getId());
            if (amount.signum() == 0) {
                if (budget != null) {
                    budgetRepository.delete(budget);
                }
            } else if (budget == null) {
                budgetRepository.save(ExpenseBudget.builder().academicYear(year).category(category).amount(amount).build());
            } else {
                budget.setAmount(amount);
            }
        }
        budgetRepository.flush();
        return summary(schoolId, year.getId(), ownerId, systemAdmin);
    }

    // ---------------------------------------------------------------- internes

    /** Catégories de l'établissement ; les catégories par défaut sont créées à la première utilisation. */
    private List<ExpenseCategory> categoriesOf(School school) {
        if (!categoryRepository.existsBySchoolId(school.getId())) {
            categoryRepository.saveAll(DEFAULT_CATEGORIES.stream()
                    .map(d -> ExpenseCategory.builder().school(school).name(d[0]).description(d[1]).systemCode(d[2]).build())
                    .toList());
        }
        return categoryRepository.findBySchoolIdOrderByNameAsc(school.getId());
    }

    private static ExpenseCategory payrollCategory(List<ExpenseCategory> categories) {
        return categories.stream().filter(ExpenseCategory::isPayroll).findFirst().orElse(null);
    }

    private static AcademicYear selectYear(List<AcademicYear> years, Long academicYearId) {
        if (academicYearId != null) {
            return years.stream().filter(y -> y.getId().equals(academicYearId)).findFirst()
                    .orElseThrow(() -> new IllegalArgumentException("Année scolaire introuvable pour cet établissement"));
        }
        return years.stream().filter(y -> Boolean.TRUE.equals(y.getIsCurrent())).findFirst()
                .orElse(years.isEmpty() ? null : years.get(0));
    }

    private void apply(Expense expense, OwnerExpenseDto.ExpenseRequest request, Long schoolId, boolean checkActive) {
        ExpenseCategory category = categoryRepository.findById(request.categoryId())
                .filter(c -> c.getSchool().getId().equals(schoolId))
                .orElseThrow(() -> new IllegalArgumentException("Catégorie inconnue pour cet établissement"));
        if (category.isPayroll()) {
            throw new IllegalArgumentException(
                    "Les salaires des enseignants proviennent de la paie : enregistrez-les depuis la fiche de l'enseignant");
        }
        if (checkActive && !category.isActive()) {
            throw new IllegalArgumentException("Cette catégorie est archivée : réactivez-la ou choisissez-en une autre");
        }
        if (request.expenseDate().isAfter(LocalDate.now(clock))) {
            throw new IllegalArgumentException("La date de la dépense ne peut pas être dans le futur");
        }
        expense.setCategory(category);
        expense.setExpenseDate(request.expenseDate());
        expense.setAmount(request.amount().setScale(2, RoundingMode.HALF_UP));
        expense.setLabel(request.label().trim());
        expense.setSupplier(blankToNull(request.supplier()));
        expense.setPaymentMethod(request.method());
        expense.setReference(blankToNull(request.reference()));
        expense.setNotes(blankToNull(request.notes()));
    }

    private static OwnerExpenseDto.CategoryLine categoryLine(ExpenseCategory c, BigDecimal budget, BigDecimal spent) {
        boolean budgeted = budget.signum() > 0;
        return new OwnerExpenseDto.CategoryLine(c.getId(), c.getName(), c.getSystemCode(), c.isActive(), budget, spent,
                budget.subtract(spent), rate(spent, budget), budgeted && spent.compareTo(budget) > 0);
    }

    private static Double rate(BigDecimal part, BigDecimal total) {
        if (total == null || total.signum() <= 0) {
            return null;
        }
        return part.multiply(HUNDRED).divide(total, 1, RoundingMode.HALF_UP).doubleValue();
    }

    private static BigDecimal sum(Stream<BigDecimal> values) {
        return values.filter(Objects::nonNull).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private static OwnerExpenseDto.ExpenseRow toRow(Expense e) {
        User author = e.getCreatedBy();
        return new OwnerExpenseDto.ExpenseRow(e.getId(), e.isManagedPayment() ? "PAYABLE" : SOURCE_MANUAL, e.getExpenseDate(), e.getCategory().getId(),
                e.getCategory().getName(), e.getLabel(), e.getSupplier(), e.getAmount(), e.getPaymentMethod().name(),
                e.getReference(), e.getNotes(), author == null ? null : fullName(author));
    }

    private static OwnerExpenseDto.ExpenseRow toRow(TeacherPayment tp, ExpenseCategory payroll) {
        String teacher = fullName(tp.getTeacher().getUser());
        String month = tp.getPayMonth().getMonth().getDisplayName(TextStyle.FULL, Locale.FRENCH) + " "
                + tp.getPayMonth().getYear();
        return new OwnerExpenseDto.ExpenseRow(tp.getId(), SOURCE_PAYROLL, tp.getPaymentDate(), payroll.getId(),
                payroll.getName(), "Paie " + month, teacher, tp.getAmount(), null, tp.getReference(), null, null);
    }

    private static OwnerExpenseDto.CategoryInfo toCategoryInfo(ExpenseCategory c, long count) {
        return new OwnerExpenseDto.CategoryInfo(c.getId(), c.getName(), c.getDescription(), c.getSystemCode(),
                c.isActive(), count);
    }

    private static OwnerExpenseDto.YearInfo toYearInfo(AcademicYear y) {
        return new OwnerExpenseDto.YearInfo(y.getId(), y.getLabel(), y.getStartDate(), y.getEndDate(),
                Boolean.TRUE.equals(y.getIsCurrent()));
    }

    private static String fullName(User user) {
        return ((user.getFirstName() == null ? "" : user.getFirstName()) + " "
                + (user.getLastName() == null ? "" : user.getLastName())).trim();
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private School requireOwnedSchool(Long schoolId, Long ownerId, boolean systemAdmin) {
        School school = schoolRepository.findById(schoolId)
                .orElseThrow(() -> new IllegalArgumentException("Établissement introuvable : " + schoolId));
        if (!systemAdmin && (school.getOwner() == null || !school.getOwner().getId().equals(ownerId))
                && !permissions.staffAllows(school.getId(), ownerId, StaffModule.EXPENSES)) {
            throw new AccessDeniedException("Vous ne pouvez gérer que les dépenses de vos établissements");
        }
        return school;
    }

    private ExpenseCategory requireOwnedCategory(Long categoryId, Long ownerId, boolean systemAdmin) {
        ExpenseCategory category = categoryRepository.findById(categoryId)
                .orElseThrow(() -> new IllegalArgumentException("Catégorie introuvable : " + categoryId));
        requireOwnedSchool(category.getSchool().getId(), ownerId, systemAdmin);
        return category;
    }

    private Expense requireOwnedExpense(Long expenseId, Long ownerId, boolean systemAdmin) {
        Expense expense = expenseRepository.findById(expenseId)
                .orElseThrow(() -> new IllegalArgumentException("Dépense introuvable : " + expenseId));
        requireOwnedSchool(expense.getSchool().getId(), ownerId, systemAdmin);
        return expense;
    }

    private static String csvCell(String value) {
        if (value == null) {
            return "";
        }
        String safe = value.replace("\"", "\"\"");
        if (!safe.isEmpty() && "=+-@".indexOf(safe.charAt(0)) >= 0) {
            safe = "'" + safe;
        }
        return safe.contains(";") || safe.contains("\"") || safe.contains("\n") ? "\"" + safe + "\"" : safe;
    }

    private static String methodLabel(String method) {
        if (method == null) {
            return "";
        }
        return switch (method) {
            case "CASH" -> "Espèces";
            case "MOBILE_MONEY" -> "Mobile money";
            case "BANK_TRANSFER" -> "Virement";
            case "CARD" -> "Carte";
            default -> method;
        };
    }
}
