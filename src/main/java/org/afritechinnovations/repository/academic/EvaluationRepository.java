package org.afritechinnovations.repository.academic;

import org.afritechinnovations.model.academic.Evaluation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface EvaluationRepository extends JpaRepository<Evaluation, Long> {

    @Query("""
        SELECT e FROM Evaluation e
        JOIN FETCH e.classSubjectTeacher cst
        JOIN FETCH cst.subject s
        JOIN FETCH cst.teacher t
        JOIN FETCH t.user u
        WHERE cst.schoolClass.id = :classId AND e.period.id = :periodId
        ORDER BY s.name ASC, e.evalDate ASC, e.id ASC
        """)
    List<Evaluation> findByClassAndPeriod(@Param("classId") Long classId, @Param("periodId") Long periodId);

    long countByPeriodId(Long periodId);
}