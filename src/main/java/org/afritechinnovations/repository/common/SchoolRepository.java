package org.afritechinnovations.repository.common;

import org.afritechinnovations.model.common.School;
import org.afritechinnovations.model.common.SchoolStatus;
import org.afritechinnovations.model.common.SchoolType;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface SchoolRepository extends JpaRepository<School, Long> {

    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @org.springframework.data.jpa.repository.Query("SELECT s FROM School s WHERE s.id = :id")
    java.util.Optional<School> lockById(@org.springframework.data.repository.query.Param("id") Long id);

    List<School> findByStatus(SchoolStatus status);

    List<School> findByType(SchoolType type);

    List<School> findByOwnerId(Long ownerId);
}
