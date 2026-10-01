package org.afritechinnovations.repository.people;

import org.afritechinnovations.model.people.TeacherExtraHour;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;

public interface TeacherExtraHourRepository extends JpaRepository<TeacherExtraHour, Long> {

    @Query("""
        SELECT e FROM TeacherExtraHour e
        JOIN FETCH e.schoolClass
        WHERE e.teacher.id = :teacherId AND e.workDate BETWEEN :from AND :to
        ORDER BY e.workDate, e.id
        """)
    List<TeacherExtraHour> findByTeacherIdAndDateBetween(@Param("teacherId") Long teacherId,
                                                         @Param("from") LocalDate from,
                                                         @Param("to") LocalDate to);
}