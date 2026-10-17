package org.afritechinnovations.repository.academic;

import org.afritechinnovations.model.academic.SchoolClass;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface SchoolClassRepository extends JpaRepository<SchoolClass, Long> {
    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @org.springframework.data.jpa.repository.Query("SELECT c FROM SchoolClass c WHERE c.id = :id")
    java.util.Optional<SchoolClass> lockForRosterImport(@org.springframework.data.repository.query.Param("id") Long id);

    List<SchoolClass> findBySchoolId(Long schoolId);

    List<SchoolClass> findByAcademicYearId(Long academicYearId);

    @Query("""
        SELECT c FROM SchoolClass c
        JOIN FETCH c.level l
        WHERE c.school.id = :schoolId AND c.academicYear.id = :academicYearId
        ORDER BY l.orderIndex
        """)
    List<SchoolClass> findAllWithLevelBySchoolAndYear(
            @Param("schoolId") Long schoolId,
            @Param("academicYearId") Long academicYearId);
}
