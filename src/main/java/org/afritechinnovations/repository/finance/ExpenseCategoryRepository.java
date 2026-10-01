package org.afritechinnovations.repository.finance;

import org.afritechinnovations.model.finance.ExpenseCategory;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ExpenseCategoryRepository extends JpaRepository<ExpenseCategory, Long> {

    List<ExpenseCategory> findBySchoolIdOrderByNameAsc(Long schoolId);

    boolean existsBySchoolId(Long schoolId);

    boolean existsBySchoolIdAndNameIgnoreCase(Long schoolId, String name);

    boolean existsBySchoolIdAndNameIgnoreCaseAndIdNot(Long schoolId, String name, Long id);
}
