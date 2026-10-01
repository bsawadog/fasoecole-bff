package org.afritechinnovations.repository.people;

import org.afritechinnovations.model.people.EnrollmentStatus;
import org.afritechinnovations.model.people.StudentEnrollment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface StudentEnrollmentRepository extends JpaRepository<StudentEnrollment, Long> {

    List<StudentEnrollment> findByStudentId(Long studentId);

    @Query("""
        SELECT se FROM StudentEnrollment se
        JOIN FETCH se.student s
        JOIN FETCH s.user u
        WHERE se.schoolClass.id = :classId
        """)
    List<StudentEnrollment> findAllWithStudentByClassId(@Param("classId") Long classId);

    List<StudentEnrollment> findBySchoolClassIdAndStatus(Long classId, EnrollmentStatus status);

    @Query("""
        SELECT se FROM StudentEnrollment se
        JOIN FETCH se.schoolClass c
        JOIN FETCH se.student s
        WHERE c.school.id = :schoolId AND se.status = org.afritechinnovations.model.people.EnrollmentStatus.ACTIVE
        """)
    List<StudentEnrollment> findActiveBySchoolId(@Param("schoolId") Long schoolId);

    List<StudentEnrollment> findByStudentIdAndSchoolClassIdAndStatus(Long studentId, Long classId, EnrollmentStatus status);

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

    @Query("""
        SELECT se FROM StudentEnrollment se
        JOIN FETCH se.student s
        JOIN FETCH s.user u
        WHERE se.schoolClass.id = :classId AND se.status IN :statuses
        ORDER BY u.lastName
        """)
    List<StudentEnrollment> findStudentsWithUserByClassIdAndStatusIn(
            @Param("classId") Long classId,
            @Param("statuses") java.util.Collection<EnrollmentStatus> statuses);

    /** Toutes les inscriptions d'une année scolaire, avec classe, élève et compte. */
    @Query("""
        SELECT se FROM StudentEnrollment se
        JOIN FETCH se.schoolClass c
        JOIN FETCH se.student s
        JOIN FETCH s.user u
        WHERE se.academicYear.id = :yearId
        ORDER BY u.lastName, u.firstName
        """)
    List<StudentEnrollment> findByYearWithStudent(@Param("yearId") Long yearId);

    List<StudentEnrollment> findByStudentIdAndAcademicYearId(Long studentId, Long academicYearId);

    @Query("""
        SELECT se.academicYear.id, se.status, COUNT(se) FROM StudentEnrollment se
        WHERE se.schoolClass.school.id = :schoolId
        GROUP BY se.academicYear.id, se.status
        """)
    List<Object[]> countBySchoolGroupedByYearAndStatus(@Param("schoolId") Long schoolId);
}