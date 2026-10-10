package org.afritechinnovations.repository.finance;

import org.afritechinnovations.model.finance.ExpenseCategory;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ExpenseCategoryRepository extends JpaRepository<ExpenseCategory, Long> {

    @org.springframework.data.jpa.repository.Query(value = "SELECT EXISTS(SELECT 1 FROM school_payables WHERE category_id=:id UNION ALL SELECT 1 FROM fixed_school_charges WHERE category_id=:id)", nativeQuery = true)
    boolean isUsedByPayables(@org.springframework.data.repository.query.Param("id") Long id);

    List<ExpenseCategory> findBySchoolIdOrderByNameAsc(Long schoolId);

    boolean existsBySchoolId(Long schoolId);

    boolean existsBySchoolIdAndNameIgnoreCase(Long schoolId, String name);

    boolean existsBySchoolIdAndNameIgnoreCaseAndIdNot(Long schoolId, String name, Long id);
}
