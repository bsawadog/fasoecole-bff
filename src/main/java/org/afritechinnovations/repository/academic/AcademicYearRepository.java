package org.afritechinnovations.repository.academic;

import org.afritechinnovations.model.academic.AcademicYear;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface AcademicYearRepository extends JpaRepository<AcademicYear, Long> {

    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @org.springframework.data.jpa.repository.Query("select y from AcademicYear y where y.school.id = :schoolId order by y.id")
    List<AcademicYear> lockSchoolYears(@org.springframework.data.repository.query.Param("schoolId") Long schoolId);

    List<AcademicYear> findBySchoolId(Long schoolId);

    Optional<AcademicYear> findBySchoolIdAndIsCurrentTrue(Long schoolId);
}
