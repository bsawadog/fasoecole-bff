package org.afritechinnovations.repository.academic;

import org.afritechinnovations.model.academic.GradeHistory;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface GradeHistoryRepository extends JpaRepository<GradeHistory, Long> {

    List<GradeHistory> findTop300ByClassIdAndPeriodIdOrderByChangedAtDescIdDesc(Long classId, Long periodId);
}