package org.afritechinnovations.security;

import org.afritechinnovations.config.GlobalExceptionHandler;
import org.afritechinnovations.config.SecurityConfig;
import org.afritechinnovations.controler.auth.AuthController;
import org.afritechinnovations.controler.common.UserController;
import org.afritechinnovations.dto.common.UserDto;
import org.afritechinnovations.model.common.User;
import org.afritechinnovations.service.auth.*;
import org.afritechinnovations.service.common.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.*;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.*;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.test.context.web.WebAppConfiguration;
import org.springframework.test.web.servlet.*;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;
import java.util.Base64;
import java.util.List;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;

@ExtendWith(SpringExtension.class)
@WebAppConfiguration
@ContextConfiguration(classes = AccountHttpSecurityTest.Config.class)
@TestPropertySource(properties = {"app.cors.allowed-origins=http://localhost:4200",
        "app.frontend.base-url=http://localhost:4200", "app.password-reset.token-expiration-minutes=30"})
class AccountHttpSecurityTest {
    @Configuration @EnableWebMvc @Import(SecurityConfig.class)
    static class Config {
        @Bean CustomUserDetailsService details() { return mock(CustomUserDetailsService.class); }
        @Bean JwtService jwt() { return new JwtService(Base64.getEncoder().encodeToString(new byte[32]), 60_000); }
        @Bean JwtAuthenticationFilter jwtFilter(JwtService jwt, CustomUserDetailsService details) { return new JwtAuthenticationFilter(jwt, details); }
        @Bean UserService users() { return mock(UserService.class); }
        @Bean AccessGuard guard() { return mock(AccessGuard.class); }
        @Bean ParentAutoAccessService parents() { return mock(ParentAutoAccessService.class); }
        @Bean PasswordResetService resets() { return mock(PasswordResetService.class); }
        @Bean EmailVerificationService verification() { return mock(EmailVerificationService.class); }
        @Bean UserController userController(UserService users, AccessGuard guard) { return new UserController(users, guard); }
        @Bean AuthController authController(AuthenticationManager manager, JwtService jwt, UserService users,
                PasswordResetService resets, ParentAutoAccessService parents, EmailVerificationService verification, CustomUserDetailsService details) {
            return new AuthController(manager, jwt, users, resets, parents, verification, details);
        }
        @Bean Probe probe() { return new Probe(); }
        @Bean GlobalExceptionHandler errors() { return new GlobalExceptionHandler(); }
    }
    @RestController static class Probe {
        @GetMapping("/api/account-flow-probe") public String business() { return "ok"; }
    }
    @Autowired WebApplicationContext context;
    @Autowired CustomUserDetailsService details;
    @Autowired UserService users;
    @Autowired AccessGuard guard;
    @Autowired JwtService jwt;
    @Autowired PasswordEncoder encoder;
    @Autowired ParentAutoAccessService parentAccess;
    MockMvc mvc;
    User user;

    @BeforeEach void setup() {
        reset(details, users, guard, parentAccess);
        mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
        user = User.builder().id(7L).email("awa@test.bf").passwordHash(encoder.encode("MotDePasseSecret"))
                .emailVerified(true).approved(true).build();
        when(details.loadUserByUsername(user.getEmail())).thenAnswer(i -> new UserPrincipal(user, List.of("PARENT")));
        when(users.findById(7L)).thenReturn(UserDto.builder().id(7L).email(user.getEmail()).build());
        when(guard.currentUserId()).thenReturn(7L);
    }
    String bearer() { return "Bearer " + jwt.generateToken(7L, user.getEmail(), List.of("PARENT"), user.getSessionVersion()); }

    @Test void unverifiedUserCanReadOwnProfileAndResendButCannotReadBusinessData() throws Exception {
        user.setEmailVerified(false); String token = bearer();
        mvc.perform(get("/api/users/me").servletPath("/api/users/me").header("Authorization", token)).andExpect(status().isOk());
        when(users.resendEmailVerification(7L)).thenReturn(false);
        mvc.perform(post("/api/users/me/email-verification").servletPath("/api/users/me/email-verification").header("Authorization", token))
                .andExpect(status().isAccepted()).andExpect(jsonPath("$.emailSent").value(false));
        mvc.perform(get("/api/account-flow-probe").servletPath("/api/account-flow-probe").header("Authorization", token)).andExpect(status().isForbidden());
    }

    @Test void pendingUserGainsBusinessAccessAfterApprovalAndLosesItAfterSessionRevocation() throws Exception {
        user.setApproved(false); String token = bearer();
        mvc.perform(get("/api/account-flow-probe").servletPath("/api/account-flow-probe").header("Authorization", token)).andExpect(status().isForbidden());
        user.setApproved(true);
        mvc.perform(get("/api/account-flow-probe").servletPath("/api/account-flow-probe").header("Authorization", token)).andExpect(status().isOk());
        user.revokeSessions();
        mvc.perform(get("/api/account-flow-probe").servletPath("/api/account-flow-probe").header("Authorization", token)).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/account-flow-probe").servletPath("/api/account-flow-probe").header("Authorization", bearer())).andExpect(status().isOk());
    }

    @Test void badCredentialsAreRejectedAndLoginIncludesCurrentSessionVersion() throws Exception {
        mvc.perform(post("/api/auth/login").contentType("application/json")
                .content("{\"email\":\"awa@test.bf\",\"password\":\"incorrect\"}")).andExpect(status().isUnauthorized());
        user.setSessionVersion(3);
        var response = mvc.perform(post("/api/auth/login").contentType("application/json")
                .content("{\"email\":\"awa@test.bf\",\"password\":\"MotDePasseSecret\"}"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        String token = new com.fasterxml.jackson.databind.ObjectMapper().readTree(response).get("token").asText();
        org.junit.jupiter.api.Assertions.assertTrue(jwt.isTokenValid(token, new UserPrincipal(user, List.of("PARENT"))));
    }

    @Test void publicRegistrationAlwaysReturnsAcceptedWithoutTokenAndInvalidEmailIsRejected() throws Exception {
        String body = "{\"firstName\":\"Awa\",\"lastName\":\"Diallo\",\"email\":\"awa@test.bf\",\"password\":\"MotDePasseSecret\",\"schoolId\":2,\"requestedRole\":\"PARENT\"}";
        var first = mvc.perform(post("/api/auth/register").contentType("application/json").content(body))
                .andExpect(status().isAccepted()).andExpect(jsonPath("$.token").doesNotExist()).andReturn().getResponse().getContentAsString();
        var second = mvc.perform(post("/api/auth/register").contentType("application/json").content(body))
                .andExpect(status().isAccepted()).andReturn().getResponse().getContentAsString();
        org.junit.jupiter.api.Assertions.assertEquals(first, second);
        mvc.perform(post("/api/auth/register").contentType("application/json").content(body.replace("awa@test.bf", "invalid")))
                .andExpect(status().isBadRequest());
        verify(users, times(2)).receiveRegistration(any());
    }

    @Test void concurrentPasswordResetCannotIssueANewSessionWithOldCredentials() throws Exception {
        doAnswer(i -> { user.revokeSessions(); user.setPasswordHash(encoder.encode("NouveauMotDePasse")); return 0; })
                .when(parentAccess).grantFromChildren(7L, false);
        mvc.perform(post("/api/auth/login").contentType("application/json")
                .content("{\"email\":\"awa@test.bf\",\"password\":\"MotDePasseSecret\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test void deactivatedAccountsAndMalformedTokensAreUnauthorizedInsteadOfCausingInternalErrors() throws Exception {
        String token = bearer(); user.setActive(false);
        mvc.perform(get("/api/users/me").servletPath("/api/users/me").header("Authorization", token))
                .andExpect(status().isUnauthorized());
        user.setActive(true);
        mvc.perform(get("/api/users/me").servletPath("/api/users/me").header("Authorization", "Bearer invalid"))
                .andExpect(status().isUnauthorized());
    }
}
