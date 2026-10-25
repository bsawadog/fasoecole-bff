package org.afritechinnovations.repository.common;
import org.afritechinnovations.model.common.AccountRecoveryEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
public interface AccountRecoveryEventRepository extends JpaRepository<AccountRecoveryEvent,Long> {
    List<AccountRecoveryEvent> findByRequestIdOrderByCreatedAtAsc(Long requestId);
}
