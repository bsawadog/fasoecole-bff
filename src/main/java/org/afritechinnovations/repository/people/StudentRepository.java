package org.afritechinnovations.repository.people;

import org.afritechinnovations.model.people.Student;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface StudentRepository extends JpaRepository<Student, Long> {

    List<Student> findBySchoolId(Long schoolId);

    Optional<Student> findBySchoolIdAndRegistrationNumber(Long schoolId, String registrationNumber);

    Optional<Student> findByUserId(Long userId);
}