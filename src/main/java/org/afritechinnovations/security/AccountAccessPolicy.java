package org.afritechinnovations.security;

/** Règle commune pour l'accès métier et les opérations disponibles avant validation. */
public final class AccountAccessPolicy {
    private AccountAccessPolicy() { }

    public static boolean allows(UserPrincipal principal, String method, String path) {
        if (!principal.isActive()) return false;
        if (principal.isApproved() && principal.isEmailVerified() && principal.isPasswordSet()) return true;
        return ("/api/users/me".equals(path) && ("GET".equals(method) || "PUT".equals(method)))
                || ("/api/users/me/password".equals(path) && "PUT".equals(method))
                || ("/api/users/me/email-verification".equals(path) && "POST".equals(method));
    }
}
