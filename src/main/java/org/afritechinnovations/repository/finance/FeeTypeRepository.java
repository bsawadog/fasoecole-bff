package org.afritechinnovations.repository.finance;

import org.afritechinnovations.model.finance.FeeType;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface FeeTypeRepository extends JpaRepository<FeeType, Long> {

    List<FeeType> findBySchoolId(Long schoolId);
}