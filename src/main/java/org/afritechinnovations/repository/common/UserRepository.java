package org.afritechinnovations.repository.common;

import org.afritechinnovations.model.common.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.Collection;

public interface UserRepository extends JpaRepository<User, Long> {
    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @org.springframework.data.jpa.repository.Query("SELECT u FROM User u WHERE u.id = :id")
    java.util.Optional<User> findByIdForUpdate(@org.springframework.data.repository.query.Param("id") Long id);

    Optional<User> findByEmail(String email);

    Optional<User> findByEmailIgnoreCase(String email);

    boolean existsByEmail(String email);

    boolean existsByEmailIgnoreCase(String email);

    List<User> findByApprovedFalseOrderByCreatedAtAsc();

    List<User> findByApprovedFalseAndRequestedSchoolIdInOrderByCreatedAtAsc(Collection<Long> schoolIds);

    List<User> findByActiveTrue();

    List<User> findByActiveTrueAndApprovedTrueOrderByLastNameAsc();
}
