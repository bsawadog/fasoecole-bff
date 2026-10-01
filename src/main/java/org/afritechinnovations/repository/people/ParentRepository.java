package org.afritechinnovations.repository.people;

import org.afritechinnovations.model.people.Parent;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface ParentRepository extends JpaRepository<Parent, Long> {

    Optional<Parent> findByUserId(Long userId);

    /**
     * Parents connus des établissements indiqués (compte parent rattaché ou enfant inscrit), filtrés par nom,
     * prénom, courriel ou téléphone (pattern déjà en minuscules et entouré de %).
     */
    @Query("""
        SELECT DISTINCT p FROM Parent p
        JOIN FETCH p.user u
        WHERE (EXISTS (SELECT 1 FROM SchoolUser su
                       WHERE su.user = u AND su.role.name = 'PARENT' AND su.school.id IN :schoolIds)
               OR EXISTS (SELECT 1 FROM ParentStudent ps
                          WHERE ps.parent = p AND ps.student.school.id IN :schoolIds))
          AND (LOWER(u.firstName) LIKE :pattern OR LOWER(u.lastName) LIKE :pattern
               OR LOWER(CONCAT(u.firstName, ' ', u.lastName)) LIKE :pattern
               OR LOWER(CONCAT(u.lastName, ' ', u.firstName)) LIKE :pattern
               OR LOWER(COALESCE(u.email, '')) LIKE :pattern OR COALESCE(u.phone, '') LIKE :pattern)
        ORDER BY u.lastName, u.firstName
        """)
    List<Parent> searchInSchools(@Param("schoolIds") Collection<Long> schoolIds, @Param("pattern") String pattern,
                                 Pageable pageable);

    @Query("""
        SELECT COUNT(p) > 0 FROM Parent p
        WHERE p.id = :parentId
          AND (EXISTS (SELECT 1 FROM SchoolUser su
                       WHERE su.user = p.user AND su.role.name = 'PARENT' AND su.school.id IN :schoolIds)
               OR EXISTS (SELECT 1 FROM ParentStudent ps
                          WHERE ps.parent = p AND ps.student.school.id IN :schoolIds))
        """)
    boolean isKnownInSchools(@Param("parentId") Long parentId, @Param("schoolIds") Collection<Long> schoolIds);
}