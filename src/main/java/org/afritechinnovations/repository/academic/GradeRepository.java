package org.afritechinnovations.repository.academic;

import org.afritechinnovations.model.academic.Grade;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface GradeRepository extends JpaRepository<Grade, Long> {

    List<Grade> findByStudentIdOrderByGradeDateAsc(Long studentId);

    List<Grade> findByStudentIdAndTerm(Long studentId, String term);

    @Query("""
        SELECT cst.subject.name AS subjectName,
               AVG(g.value / g.maxValue * 20) AS averageOn20
        FROM Grade g
        JOIN g.classSubjectTeacher cst
        WHERE g.student.id = :studentId AND g.term = :term
        GROUP BY cst.subject.name
        """)
    List<Object[]> findAverageBySubjectForStudentAndTerm(
            @Param("studentId") Long studentId,
            @Param("term") String term);

    @Query("""
        SELECT g.student.id AS studentId,
               AVG(g.value / g.maxValue * 20) AS overallAverage
        FROM Grade g
        JOIN StudentEnrollment se ON se.student = g.student
        WHERE se.schoolClass.id = :classId AND g.term = :term
        GROUP BY g.student.id
        ORDER BY overallAverage DESC
        """)
    List<Object[]> findClassRanking(@Param("classId") Long classId, @Param("term") String term);
}