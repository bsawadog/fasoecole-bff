package org.afritechinnovations.repository.people;

import org.afritechinnovations.model.people.EnrollmentStatus;
import org.afritechinnovations.model.people.StudentEnrollment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface StudentEnrollmentRepository extends JpaRepository<StudentEnrollment, Long> {

    List<StudentEnrollment> findByStudentId(Long studentId);

    List<StudentEnrollment> findBySchoolClassIdAndStatus(Long classId, EnrollmentStatus status);

    @Query("""
        SELECT se FROM StudentEnrollment se
        JOIN FETCH se.student s
        JOIN FETCH s.user u
        WHERE se.schoolClass.id = :classId AND se.status = :status
        ORDER BY u.lastName
        """)
    List<StudentEnrollment> findActiveStudentsWithUserByClassId(
            @Param("classId") Long classId,
            @Param("status") EnrollmentStatus status);
}