package org.afritechinnovations.repository.people;

import org.afritechinnovations.model.people.TeacherScheduleSlot;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface TeacherScheduleSlotRepository extends JpaRepository<TeacherScheduleSlot, Long> {

    @Query("""
        SELECT s FROM TeacherScheduleSlot s
        JOIN FETCH s.schoolClass c
        JOIN FETCH c.academicYear
        WHERE s.teacher.id = :teacherId
        ORDER BY s.dayOfWeek, s.startTime, s.effectiveFrom
        """)
    List<TeacherScheduleSlot> findAllWithClassByTeacherId(@Param("teacherId") Long teacherId);
}