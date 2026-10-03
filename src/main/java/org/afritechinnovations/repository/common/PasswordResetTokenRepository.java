package org.afritechinnovations.repository.common;

import org.afritechinnovations.model.common.PasswordResetToken;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface PasswordResetTokenRepository extends JpaRepository<PasswordResetToken, Long> {

    Optional<PasswordResetToken> findByTokenHash(String tokenHash);

    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @org.springframework.data.jpa.repository.Query("SELECT t FROM PasswordResetToken t WHERE t.tokenHash = :hash")
    Optional<PasswordResetToken> findByTokenHashForUpdate(@org.springframework.data.repository.query.Param("hash") String hash);

    void deleteByUserId(Long userId);
}
