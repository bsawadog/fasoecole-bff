package org.afritechinnovations.repository.academic;

import org.afritechinnovations.model.academic.ReportCard;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ReportCardRepository extends JpaRepository<ReportCard, Long> {

    List<ReportCard> findByStudentId(Long studentId);

    Optional<ReportCard> findByStudentIdAndAcademicYearIdAndTerm(
            Long studentId, Long academicYearId, String term);
}