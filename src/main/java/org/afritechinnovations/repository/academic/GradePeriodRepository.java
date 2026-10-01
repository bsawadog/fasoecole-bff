package org.afritechinnovations.repository.academic;

import org.afritechinnovations.model.academic.GradePeriod;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface GradePeriodRepository extends JpaRepository<GradePeriod, Long> {

    @Query("""
        SELECT p FROM GradePeriod p JOIN FETCH p.academicYear y
        WHERE p.school.id = :schoolId
        ORDER BY y.startDate DESC, p.startDate ASC
        """)
    List<GradePeriod> findBySchoolIdOrdered(@Param("schoolId") Long schoolId);

    boolean existsBySchoolIdAndAcademicYearIdAndCodeIgnoreCase(Long schoolId, Long academicYearId, String code);

    boolean existsBySchoolIdAndAcademicYearIdAndCodeIgnoreCaseAndIdNot(Long schoolId, Long academicYearId,
                                                                      String code, Long id);
}