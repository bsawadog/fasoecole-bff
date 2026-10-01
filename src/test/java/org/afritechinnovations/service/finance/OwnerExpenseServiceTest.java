package org.afritechinnovations.service.finance;

import org.afritechinnovations.dto.finance.OwnerExpenseDto;
import org.afritechinnovations.model.academic.AcademicYear;
import org.afritechinnovations.model.common.School;
import org.afritechinnovations.model.common.StaffModule;
import org.afritechinnovations.model.common.User;
import org.afritechinnovations.model.finance.Expense;
import org.afritechinnovations.model.finance.ExpenseBudget;
import org.afritechinnovations.model.finance.ExpenseCategory;
import org.afritechinnovations.model.finance.PaymentMethod;
import org.afritechinnovations.model.people.Teacher;
import org.afritechinnovations.model.people.TeacherPayment;
import org.afritechinnovations.repository.academic.AcademicYearRepository;
import org.afritechinnovations.repository.common.SchoolRepository;
import org.afritechinnovations.repository.common.UserRepository;
import org.afritechinnovations.repository.finance.ExpenseBudgetRepository;
import org.afritechinnovations.repository.finance.ExpenseCategoryRepository;
import org.afritechinnovations.repository.finance.ExpenseRepository;
import org.afritechinnovations.repository.finance.PaymentRepository;
import org.afritechinnovations.repository.people.TeacherPaymentRepository;
import org.afritechinnovations.security.SchoolPermissions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.security.access.AccessDeniedException;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class OwnerExpenseServiceTest {

    private static final Long OWNER_ID = 10L;

    @Mock SchoolRepository schoolRepository;
    @Mock ExpenseCategoryRepository categoryRepository;
    @Mock ExpenseRepository expenseRepository;
    @Mock ExpenseBudgetRepository budgetRepository;
    @Mock AcademicYearRepository academicYearRepository;
    @Mock PaymentRepository paymentRepository;
    @Mock TeacherPaymentRepository teacherPaymentRepository;
    @Mock UserRepository userRepository;
    @Mock
    SchoolPermissions permissions;

    @InjectMocks OwnerExpenseService service;

    private School school;
    private AcademicYear year;
    private ExpenseCategory payroll;
    private ExpenseCategory rent;
    private ExpenseCategory supplies;

    @BeforeEach
    void setUp() {
        service.setClock(Clock.fixed(LocalDate.of(2026, 10, 20).atStartOfDay().toInstant(ZoneOffset.UTC), ZoneOffset.UTC));
        school = School.builder().id(1L).name("École ABC").owner(User.builder().id(OWNER_ID).build()).build();
        year = AcademicYear.builder().id(2L).school(school).label("2026-2027").isCurrent(true)
                .startDate(LocalDate.of(2026, 9, 1)).endDate(LocalDate.of(2027, 6, 30)).build();
        payroll = ExpenseCategory.builder().id(30L).school(school).name("Salaires des enseignants")
                .systemCode(ExpenseCategory.PAYROLL).build();
        rent = ExpenseCategory.builder().id(31L).school(school).name("Loyer & locaux").build();
        supplies = ExpenseCategory.builder().id(32L).school(school).name("Fournitures").build();

        when(schoolRepository.findById(1L)).thenReturn(Optional.of(school));
        when(categoryRepository.existsBySchoolId(1L)).thenReturn(true);
        when(categoryRepository.findBySchoolIdOrderByNameAsc(1L)).thenReturn(List.of(rent, supplies, payroll));
        when(categoryRepository.findById(31L)).thenReturn(Optional.of(rent));
        when(categoryRepository.findById(30L)).thenReturn(Optional.of(payroll));
        when(academicYearRepository.findBySchoolId(1L)).thenReturn(List.of(year));
        when(academicYearRepository.findById(2L)).thenReturn(Optional.of(year));
        when(expenseRepository.findForSchoolBetween(eq(1L), any(), any())).thenReturn(List.of());
        when(teacherPaymentRepository.findForSchoolBetween(eq(1L), any(), any())).thenReturn(List.of());
    }

    @Test
    void summaryCombinesTuitionIncomeManualExpensesPayrollAndBudget() {
        LocalDate from = year.getStartDate();
        LocalDate to = year.getEndDate();
        when(paymentRepository.findAmountsForSchoolBetween(1L, from, to)).thenReturn(List.of(
                new Object[]{LocalDate.of(2026, 9, 5), new BigDecimal("300000")},
                new Object[]{LocalDate.of(2026, 10, 3), new BigDecimal("200000")}));
        when(expenseRepository.findForSchoolBetween(1L, from, to)).thenReturn(List.of(
                expense(1L, rent, LocalDate.of(2026, 9, 1), "150000"),
                expense(2L, rent, LocalDate.of(2026, 10, 1), "150000"),
                expense(3L, supplies, LocalDate.of(2026, 10, 2), "40000")));
        when(teacherPaymentRepository.findForSchoolBetween(1L, from, to)).thenReturn(List.of(
                teacherPayment(7L, LocalDate.of(2026, 9, 30), "100000")));
        when(budgetRepository.findByAcademicYearId(2L)).thenReturn(List.of(
                ExpenseBudget.builder().academicYear(year).category(rent).amount(new BigDecimal("250000")).build(),
                ExpenseBudget.builder().academicYear(year).category(payroll).amount(new BigDecimal("1000000")).build()));

        OwnerExpenseDto.Summary summary = service.summary(1L, null, OWNER_ID, false);

        assertEquals(new BigDecimal("500000"), summary.income());
        assertEquals(new BigDecimal("440000"), summary.expenses());
        assertEquals(new BigDecimal("60000"), summary.balance());
        assertEquals(10, summary.months().size());
        OwnerExpenseDto.MonthLine september = summary.months().get(0);
        assertEquals("2026-09", september.month());
        assertEquals(new BigDecimal("250000"), september.expenses());
        assertEquals(new BigDecimal("50000"), september.balance());

        OwnerExpenseDto.CategoryLine rentLine = line(summary, 31L);
        assertEquals(new BigDecimal("300000"), rentLine.spent());
        assertTrue(rentLine.overBudget());
        assertEquals(120.0, rentLine.usedRate());
        OwnerExpenseDto.CategoryLine payrollLine = line(summary, 30L);
        assertEquals(new BigDecimal("100000"), payrollLine.spent());
        assertFalse(payrollLine.overBudget());
        assertEquals(1, summary.overBudgetCount());
        assertEquals(new BigDecimal("1250000"), summary.budgetTotal());
    }

    @Test
    void manualExpensesCannotUseThePayrollCategoryNorAFutureDate() {
        OwnerExpenseDto.ExpenseRequest onPayroll = request(30L, LocalDate.of(2026, 10, 1));
        assertThrows(IllegalArgumentException.class, () -> service.createExpense(1L, onPayroll, OWNER_ID, false));
        OwnerExpenseDto.ExpenseRequest future = request(31L, LocalDate.of(2026, 11, 1));
        assertThrows(IllegalArgumentException.class, () -> service.createExpense(1L, future, OWNER_ID, false));
        verify(expenseRepository, never()).save(any());

        when(expenseRepository.save(any(Expense.class))).thenAnswer(inv -> inv.getArgument(0));
        OwnerExpenseDto.ExpenseRow row = service.createExpense(1L, request(31L, LocalDate.of(2026, 10, 1)), OWNER_ID, false);
        assertEquals("Loyer & locaux", row.categoryName());
        assertEquals(OwnerExpenseService.SOURCE_MANUAL, row.source());
    }

    @Test
    void anotherOwnerCannotSeeTheExpenses() {
        assertThrows(AccessDeniedException.class, () -> service.summary(1L, null, 99L, false));
        assertThrows(AccessDeniedException.class, () -> service.listExpenses(1L, null, null, null, 99L, false));
    }

    @Test
    void staffWithTheExpensesModuleCanWorkOnTheSchool() {
        when(permissions.staffAllows(1L, 55L, StaffModule.EXPENSES)).thenReturn(true);
        assertEquals(0, service.listExpenses(1L, null, null, null, 55L, false).size());
        assertThrows(AccessDeniedException.class, () -> service.listExpenses(1L, null, null, null, 56L, false));
    }

    @Test
    void defaultCategoriesAreCreatedOnFirstUseAndZeroBudgetRemovesTheLine() {
        when(categoryRepository.existsBySchoolId(1L)).thenReturn(false);
        when(expenseRepository.countByCategoryForSchool(1L)).thenReturn(List.of());
        service.listCategories(1L, OWNER_ID, false);
        @SuppressWarnings({"unchecked", "rawtypes"})
        ArgumentCaptor<List<ExpenseCategory>> captor = ArgumentCaptor.forClass((Class) List.class);
        verify(categoryRepository).saveAll(captor.capture());
        List<ExpenseCategory> seeded = captor.getValue();
        assertTrue(seeded.stream().anyMatch(ExpenseCategory::isPayroll));

        when(categoryRepository.existsBySchoolId(1L)).thenReturn(true);
        ExpenseBudget existing = ExpenseBudget.builder().id(5L).academicYear(year).category(rent)
                .amount(new BigDecimal("100")).build();
        when(budgetRepository.findByAcademicYearId(2L)).thenReturn(List.of(existing));
        service.saveBudget(1L, new OwnerExpenseDto.BudgetRequest(2L, List.of(
                new OwnerExpenseDto.BudgetLine(31L, BigDecimal.ZERO),
                new OwnerExpenseDto.BudgetLine(32L, new BigDecimal("50000")))), OWNER_ID, false);
        verify(budgetRepository).delete(existing);
        verify(budgetRepository).save(any(ExpenseBudget.class));
    }

    private static OwnerExpenseDto.CategoryLine line(OwnerExpenseDto.Summary summary, Long categoryId) {
        return summary.categories().stream().filter(l -> l.categoryId().equals(categoryId)).findFirst().orElseThrow();
    }

    private Expense expense(Long id, ExpenseCategory category, LocalDate date, String amount) {
        return Expense.builder().id(id).school(school).category(category).expenseDate(date)
                .amount(new BigDecimal(amount)).label("Dépense " + id).paymentMethod(PaymentMethod.CASH).build();
    }

    private TeacherPayment teacherPayment(Long id, LocalDate date, String amount) {
        Teacher teacher = Teacher.builder().id(8L).school(school)
                .user(User.builder().firstName("Paul").lastName("Ouédraogo").build()).build();
        return TeacherPayment.builder().id(id).teacher(teacher).payMonth(date.withDayOfMonth(1)).paymentDate(date)
                .amount(new BigDecimal(amount)).build();
    }

    private static OwnerExpenseDto.ExpenseRequest request(Long categoryId, LocalDate date) {
        return new OwnerExpenseDto.ExpenseRequest(categoryId, date, new BigDecimal("150000"), "Loyer octobre",
                "Bailleur", PaymentMethod.BANK_TRANSFER, null, null);
    }
}
