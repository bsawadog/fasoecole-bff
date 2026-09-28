package org.afritechinnovations.repository.common;

import org.afritechinnovations.model.common.School;
import org.afritechinnovations.model.common.SchoolStatus;
import org.afritechinnovations.model.common.SchoolType;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface SchoolRepository extends JpaRepository<School, Long> {

    List<School> findByStatus(SchoolStatus status);

    List<School> findByType(SchoolType type);

    List<School> findByOwnerId(Long ownerId);
}