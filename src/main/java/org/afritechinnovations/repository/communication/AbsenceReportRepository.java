package org.afritechinnovations.repository.communication;

import org.afritechinnovations.model.communication.AbsenceReport;
import org.afritechinnovations.model.communication.AbsenceReportStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface AbsenceReportRepository extends JpaRepository<AbsenceReport, Long> {

    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT r FROM AbsenceReport r WHERE r.id = :id")
    java.util.Optional<AbsenceReport> findByIdForUpdate(@Param("id") Long id);

    @Query("""
        SELECT r FROM AbsenceReport r
        LEFT JOIN FETCH r.reportedBy
        LEFT JOIN FETCH r.handledBy
        WHERE r.student.id = :studentId
        ORDER BY r.startDate DESC, r.id DESC
        """)
    List<AbsenceReport> findByStudent(@Param("studentId") Long studentId);

    @Query("""
        SELECT r FROM AbsenceReport r
        JOIN FETCH r.student s
        JOIN FETCH s.user
        LEFT JOIN FETCH r.reportedBy
        LEFT JOIN FETCH r.handledBy
        WHERE r.school.id = :schoolId
        ORDER BY r.createdAt DESC, r.id DESC
        """)
    List<AbsenceReport> findBySchool(@Param("schoolId") Long schoolId);

    long countBySchoolIdAndStatus(Long schoolId, AbsenceReportStatus status);

    @Query("""
        SELECT r FROM AbsenceReport r
        WHERE r.student.id = :studentId AND r.status = :status
          AND :date BETWEEN r.startDate AND r.endDate
        ORDER BY r.createdAt DESC
        """)
    List<AbsenceReport> findCovering(@Param("studentId") Long studentId,
                                     @Param("date") java.time.LocalDate date,
                                     @Param("status") AbsenceReportStatus status);
}
