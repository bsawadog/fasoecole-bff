package org.afritechinnovations.repository.people;

import org.afritechinnovations.model.people.TeacherRate;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.Optional;

public interface TeacherRateRepository extends JpaRepository<TeacherRate, Long> {

    Optional<TeacherRate> findFirstByTeacherIdAndEffectiveFromLessThanEqualOrderByEffectiveFromDesc(
            Long teacherId, LocalDate date);

    Optional<TeacherRate> findByTeacherIdAndEffectiveFrom(Long teacherId, LocalDate effectiveFrom);
}