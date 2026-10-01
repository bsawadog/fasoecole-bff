package org.afritechinnovations.repository.finance;

import org.afritechinnovations.model.finance.Expense;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;

public interface ExpenseRepository extends JpaRepository<Expense, Long> {

    @Query("""
        SELECT e FROM Expense e
        JOIN FETCH e.category
        LEFT JOIN FETCH e.createdBy
        WHERE e.school.id = :schoolId AND e.expenseDate BETWEEN :from AND :to
        ORDER BY e.expenseDate DESC, e.id DESC
        """)
    List<Expense> findForSchoolBetween(@Param("schoolId") Long schoolId, @Param("from") LocalDate from,
                                       @Param("to") LocalDate to);

    @Query("SELECT e.category.id, COUNT(e) FROM Expense e WHERE e.school.id = :schoolId GROUP BY e.category.id")
    List<Object[]> countByCategoryForSchool(@Param("schoolId") Long schoolId);

    boolean existsByCategoryId(Long categoryId);
}
