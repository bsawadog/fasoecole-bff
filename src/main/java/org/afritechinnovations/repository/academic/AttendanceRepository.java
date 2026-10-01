package org.afritechinnovations.repository.academic;

import org.afritechinnovations.model.academic.Attendance;
import org.afritechinnovations.model.academic.AttendanceStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;

public interface AttendanceRepository extends JpaRepository<Attendance, Long> {

    List<Attendance> findByStudentIdOrderByAttendanceDateDesc(Long studentId);

    @Query("""
        SELECT a FROM Attendance a
        WHERE a.schoolClass.id = :classId AND a.attendanceDate BETWEEN :start AND :end
        """)
    List<Attendance> findByClassBetween(@Param("classId") Long classId,
                                        @Param("start") LocalDate start,
                                        @Param("end") LocalDate end);

    @Query("""
        SELECT a FROM Attendance a
        JOIN FETCH a.student s
        JOIN FETCH s.user u
        WHERE a.schoolClass.id = :classId
          AND a.status = :status
          AND a.justification IS NULL
          AND a.attendanceDate BETWEEN :start AND :end
        """)
    List<Attendance> findUnjustifiedAbsences(
            @Param("classId") Long classId,
            @Param("status") AttendanceStatus status,
            @Param("start") LocalDate start,
            @Param("end") LocalDate end);

    @Query("""
        SELECT a.student.id AS studentId,
               (100.0 * SUM(CASE WHEN a.status = 'PRESENT' THEN 1 ELSE 0 END) / COUNT(a)) AS attendanceRate
        FROM Attendance a
        GROUP BY a.student.id
        """)
    List<Object[]> findAttendanceRateByStudent();
}