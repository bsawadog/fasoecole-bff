package org.afritechinnovations.security;

import lombok.RequiredArgsConstructor;
import org.afritechinnovations.model.common.SchoolStaff;
import org.afritechinnovations.model.common.StaffModule;
import org.afritechinnovations.repository.common.SchoolStaffRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** Résout les accès délégués du personnel administratif (le propriétaire est vérifié par l'appelant). */
@Component
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class SchoolPermissions {

    private final SchoolStaffRepository staffRepository;

    /** Vrai si {@code userId} est un membre actif du personnel de l'établissement avec accès au module. */
    public boolean staffAllows(Long schoolId, Long userId, StaffModule module) {
        if (schoolId == null || userId == null || module == null) {
            return false;
        }
        return staffRepository.findBySchoolIdAndUserId(schoolId, userId)
                .map(staff -> staff.allows(module))
                .orElse(false);
    }

    /** Vrai si {@code userId} est un membre actif du personnel de l'établissement (quel que soit le module). */
    public boolean isActiveStaff(Long schoolId, Long userId) {
        if (schoolId == null || userId == null) {
            return false;
        }
        return staffRepository.findBySchoolIdAndUserId(schoolId, userId)
                .map(SchoolStaff::isActive)
                .orElse(false);
    }
}
