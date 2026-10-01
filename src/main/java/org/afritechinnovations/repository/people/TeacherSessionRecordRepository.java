package org.afritechinnovations.repository.people;

import org.afritechinnovations.model.people.TeacherSessionRecord;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface TeacherSessionRecordRepository extends JpaRepository<TeacherSessionRecord, Long> {

    @Query("""
        SELECT r FROM TeacherSessionRecord r
        WHERE r.slot.teacher.id = :teacherId AND r.sessionDate BETWEEN :from AND :to
        """)
    List<TeacherSessionRecord> findByTeacherIdAndDateBetween(@Param("teacherId") Long teacherId,
                                                             @Param("from") LocalDate from,
                                                             @Param("to") LocalDate to);

    Optional<TeacherSessionRecord> findBySlotIdAndSessionDate(Long slotId, LocalDate sessionDate);

    Optional<TeacherSessionRecord> findFirstBySlotIdOrderBySessionDateDesc(Long slotId);
}