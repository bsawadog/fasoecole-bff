package org.afritechinnovations.security;

import lombok.RequiredArgsConstructor;
import org.afritechinnovations.model.common.School;
import org.afritechinnovations.model.common.SchoolStatus;
import org.afritechinnovations.repository.common.SchoolRepository;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Component;

/** Operational modules start after approval; school request metadata remains accessible. */
@Component
@RequiredArgsConstructor
public class SchoolApprovalPolicy {
    private final SchoolRepository schools;
    private final AccessGuard guard;

    public void requireApproved(Long schoolId) {
        guard.requireSchoolMember(schoolId);
        School school = schools.findById(schoolId)
                .orElseThrow(() -> new IllegalArgumentException("Établissement introuvable"));
        requireApproved(school, guard.isSuperAdmin());
    }

    public static void requireApproved(School school, boolean superAdmin) {
        if (!superAdmin && school.getStatus() != SchoolStatus.ACTIVE)
            throw new AccessDeniedException("Les informations seront disponibles après approbation de la demande d’établissement");
    }
}
