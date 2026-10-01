package org.afritechinnovations.repository.common;

import org.afritechinnovations.model.common.RoleName;
import org.afritechinnovations.model.common.SchoolAccessRequest;
import org.afritechinnovations.model.common.SchoolAccessStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;

public interface SchoolAccessRequestRepository extends JpaRepository<SchoolAccessRequest, Long> {

    List<SchoolAccessRequest> findByUserIdOrderByCreatedAtDesc(Long userId);

    boolean existsByUserIdAndSchoolIdAndRequestedRoleAndStatus(
            Long userId, Long schoolId, RoleName requestedRole, SchoolAccessStatus status);

    List<SchoolAccessRequest> findByStatusOrderByCreatedAtAsc(SchoolAccessStatus status);

    List<SchoolAccessRequest> findByStatusAndSchoolIdInOrderByCreatedAtAsc(
            SchoolAccessStatus status, Collection<Long> schoolIds);

    List<SchoolAccessRequest> findByStatusInOrderByCreatedAtAsc(Collection<SchoolAccessStatus> statuses);

    List<SchoolAccessRequest> findByStatusInAndSchoolIdInOrderByCreatedAtAsc(
            Collection<SchoolAccessStatus> statuses, Collection<Long> schoolIds);

    List<SchoolAccessRequest> findByUserIdAndSchoolIdAndRequestedRole(Long userId, Long schoolId, RoleName requestedRole);
}
