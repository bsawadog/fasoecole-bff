package org.afritechinnovations.repository.finance;

import org.afritechinnovations.model.finance.FeeType;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface FeeTypeRepository extends JpaRepository<FeeType, Long> {

    List<FeeType> findBySchoolId(Long schoolId);

    @org.springframework.data.jpa.repository.Query(
            "SELECT f FROM FeeType f LEFT JOIN FETCH f.level WHERE f.school.id = :schoolId ORDER BY f.active DESC, f.name ASC")
    List<FeeType> findWithLevelBySchoolId(@org.springframework.data.repository.query.Param("schoolId") Long schoolId);
}