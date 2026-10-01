package org.afritechinnovations.repository.people;

import jakarta.persistence.LockModeType;
import org.afritechinnovations.model.people.Teacher;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface TeacherRepository extends JpaRepository<Teacher, Long> {

    List<Teacher> findBySchoolId(Long schoolId);

    List<Teacher> findByUserId(Long userId);

    /** Verrou pessimiste : sérialise les écritures de paie d'un même enseignant (anti sur-paiement concurrent). */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT t FROM Teacher t WHERE t.id = :id")
    Optional<Teacher> findByIdForUpdate(@Param("id") Long id);
}