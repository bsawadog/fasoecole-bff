package org.afritechinnovations.security;
import org.afritechinnovations.model.common.RoleName;
import java.util.Set;
public final class RegistrationRoles {
    private static final Set<RoleName> ALLOWED = Set.of(RoleName.TEACHER, RoleName.PARENT, RoleName.STUDENT);
    private RegistrationRoles() {}
    public static void requireAllowed(RoleName role) {
        if (role == null || !ALLOWED.contains(role)) throw new IllegalArgumentException("Le profil demandé doit être enseignant, parent ou étudiant");
    }
}
