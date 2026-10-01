package org.afritechinnovations.repository.people;

import org.afritechinnovations.model.people.Student;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface StudentRepository extends JpaRepository<Student, Long> {

    List<Student> findBySchoolId(Long schoolId);

    Optional<Student> findBySchoolIdAndRegistrationNumber(Long schoolId, String registrationNumber);

    Optional<Student> findByUserId(Long userId);

    /** Un même compte peut être élève dans plusieurs établissements. */
    List<Student> findAllByUserId(Long userId);

    @Query("SELECT s.registrationNumber FROM Student s WHERE s.school.id = :schoolId AND s.registrationNumber LIKE :prefix%")
    List<String> findRegistrationNumbersBySchoolAndPrefix(@Param("schoolId") Long schoolId, @Param("prefix") String prefix);
}