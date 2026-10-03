package org.afritechinnovations.security;

import org.afritechinnovations.model.common.User;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class AccountAccessPolicyTest {
    private UserPrincipal principal(boolean active, boolean approved, boolean verified, boolean passwordSet, String role) {
        return new UserPrincipal(User.builder().id(7L).active(active).approved(approved)
                .emailVerified(verified).passwordSet(passwordSet).build(), List.of(role));
    }

    @ParameterizedTest
    @ValueSource(strings = {"TEACHER", "PARENT", "STUDENT", "STAFF", "SCHOOL_ADMIN", "SUPER_ADMIN"})
    void everyRoleNeedsVerifiedEmailAndApprovalForBusinessAccess(String role) {
        assertFalse(AccountAccessPolicy.allows(principal(true, true, false, true, role), "GET", "/api/conversations"));
        assertFalse(AccountAccessPolicy.allows(principal(true, false, true, true, role), "GET", "/api/conversations"));
        assertFalse(AccountAccessPolicy.allows(principal(true, true, true, false, role), "GET", "/api/conversations"));
        assertTrue(AccountAccessPolicy.allows(principal(true, true, true, true, role), "GET", "/api/conversations"));
    }

    @Test
    void pendingUserCanSeeAndEditOwnProfileAndResendButCannotReadOtherUsers() {
        var pending = principal(true, false, false, true, "PARENT");
        assertTrue(AccountAccessPolicy.allows(pending, "GET", "/api/users/me"));
        assertTrue(AccountAccessPolicy.allows(pending, "PUT", "/api/users/me"));
        assertTrue(AccountAccessPolicy.allows(pending, "PUT", "/api/users/me/password"));
        assertTrue(AccountAccessPolicy.allows(pending, "POST", "/api/users/me/email-verification"));
        assertFalse(AccountAccessPolicy.allows(pending, "GET", "/api/users/7"));
        assertFalse(AccountAccessPolicy.allows(pending, "GET", "/api/users/me/school-requests"));
        assertFalse(AccountAccessPolicy.allows(pending, "DELETE", "/api/users/me"));
    }

    @Test
    void inactiveAccountsCannotUseEvenProfileOperations() {
        var inactive = principal(false, true, true, true, "SUPER_ADMIN");
        assertFalse(AccountAccessPolicy.allows(inactive, "GET", "/api/users/me"));
        assertFalse(AccountAccessPolicy.allows(inactive, "POST", "/api/users/me/email-verification"));
    }
}
