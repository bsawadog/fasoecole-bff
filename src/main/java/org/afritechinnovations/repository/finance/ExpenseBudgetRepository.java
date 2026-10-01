package org.afritechinnovations.repository.finance;

import org.afritechinnovations.model.finance.ExpenseBudget;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ExpenseBudgetRepository extends JpaRepository<ExpenseBudget, Long> {

    List<ExpenseBudget> findByAcademicYearId(Long academicYearId);
}
