package org.afritechinnovations.repository.common;

import org.afritechinnovations.model.common.AccountRecoveryRequest;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;
import java.util.*;

public interface AccountRecoveryRequestRepository extends JpaRepository<AccountRecoveryRequest,Long> {
    List<AccountRecoveryRequest> findTop100BySchoolOwnerIdOrderByCreatedAtDesc(Long ownerId);
    List<AccountRecoveryRequest> findTop100BySchoolIdOrderByCreatedAtDesc(Long schoolId);
    List<AccountRecoveryRequest> findTop100ByOrderByCreatedAtDesc();
    List<AccountRecoveryRequest> findByUserIdAndStatusIn(Long userId, Collection<AccountRecoveryRequest.Status> statuses);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from AccountRecoveryRequest r where r.id=:id")
    Optional<AccountRecoveryRequest> lockById(@Param("id") Long id);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from AccountRecoveryRequest r where r.tokenHash=:hash")
    Optional<AccountRecoveryRequest> findByTokenHashForUpdate(@Param("hash") String hash);
}
