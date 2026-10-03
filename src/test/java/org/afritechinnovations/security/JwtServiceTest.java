package org.afritechinnovations.security;

import org.afritechinnovations.model.common.User;
import org.junit.jupiter.api.Test;
import java.util.Base64;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class JwtServiceTest {
    private final JwtService jwt = new JwtService(Base64.getEncoder().encodeToString(new byte[32]), 60_000);

    @Test
    void passwordChangeInvalidatesAllOldTokensAndANewLoginWorks() {
        User user = User.builder().id(7L).email("awa@test.bf").build();
        String first = jwt.generateToken(7L, user.getEmail(), List.of("PARENT"), user.getSessionVersion());
        String second = jwt.generateToken(7L, user.getEmail(), List.of("PARENT"), user.getSessionVersion());
        assertTrue(jwt.isTokenValid(first, new UserPrincipal(user, List.of("PARENT"))));
        user.revokeSessions();
        assertFalse(jwt.isTokenValid(first, new UserPrincipal(user, List.of("PARENT"))));
        assertFalse(jwt.isTokenValid(second, new UserPrincipal(user, List.of("PARENT"))));
        String next = jwt.generateToken(7L, user.getEmail(), List.of("PARENT"), user.getSessionVersion());
        assertTrue(jwt.isTokenValid(next, new UserPrincipal(user, List.of("PARENT"))));
    }

    @Test
    void reusedEmailDoesNotMakeOldTokenValidForAnotherAccount() {
        String token = jwt.generateToken(7L, "awa@test.bf", List.of("PARENT"));
        assertFalse(jwt.isTokenValid(token, new UserPrincipal(User.builder().id(8L).email("awa@test.bf").build(), List.of("PARENT"))));
    }

    @Test
    void expiredAndAlteredTokensAreRejected() {
        var expired = new JwtService(Base64.getEncoder().encodeToString(new byte[32]), -1000);
        String token = expired.generateToken(7L, "awa@test.bf", List.of("PARENT"));
        var user = new UserPrincipal(User.builder().id(7L).email("awa@test.bf").build(), List.of("PARENT"));
        assertThrows(io.jsonwebtoken.ExpiredJwtException.class, () -> jwt.isTokenValid(token, user));
        String valid = jwt.generateToken(7L, "awa@test.bf", List.of("PARENT"));
        String corrupted = "x" + valid.substring(1);
        assertThrows(io.jsonwebtoken.JwtException.class, () -> jwt.isTokenValid(corrupted, user));
    }
}
