package org.afritechinnovations.controler.auth;

import lombok.RequiredArgsConstructor;
import org.afritechinnovations.dto.auth.LoginRequest;
import org.afritechinnovations.dto.auth.LoginResponse;
import org.afritechinnovations.dto.auth.ForgotPasswordRequest;
import org.afritechinnovations.dto.auth.ResetPasswordRequest;
import org.afritechinnovations.dto.auth.RegisterUserRequest;
import org.afritechinnovations.security.JwtService;
import org.afritechinnovations.security.UserPrincipal;
import org.afritechinnovations.service.auth.PasswordResetService;
import org.afritechinnovations.service.common.UserService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.web.bind.annotation.*;
import jakarta.validation.Valid;

import java.util.Map;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthenticationManager authenticationManager;
    private final JwtService jwtService;
    private final UserService userService;
    private final PasswordResetService passwordResetService;

    @PostMapping("/login")
    public ResponseEntity<LoginResponse> login(@RequestBody LoginRequest request) {
        try {
            var authentication = authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(request.getEmail(), request.getPassword()));

            UserPrincipal principal = (UserPrincipal) authentication.getPrincipal();
            String token = jwtService.generateToken(principal.getId(), principal.getEmail(), principal.getRoles());

            return ResponseEntity.ok(LoginResponse.builder()
                    .token(token)
                    .userId(principal.getId())
                    .email(principal.getEmail())
                    .roles(principal.getRoles())
                    .build());
        } catch (AuthenticationException ex) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
    }

    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    public LoginResponse register(@Valid @RequestBody RegisterUserRequest request) {
        userService.registerPending(request);
        var authentication = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(request.getEmail(), request.getPassword()));
        UserPrincipal principal = (UserPrincipal) authentication.getPrincipal();
        String token = jwtService.generateToken(principal.getId(), principal.getEmail(), principal.getRoles());
        return LoginResponse.builder()
                .token(token)
                .userId(principal.getId())
                .email(principal.getEmail())
                .roles(principal.getRoles())
                .build();
    }

    @PostMapping("/forgot-password")
    public ResponseEntity<Map<String, String>> forgotPassword(
            @Valid @RequestBody ForgotPasswordRequest request) {
        passwordResetService.requestReset(request.getEmail());
        return ResponseEntity.accepted().body(Map.of(
                "message",
                "Si un compte actif correspond à cette adresse, un lien de réinitialisation va être envoyé."
        ));
    }

    @PostMapping("/reset-password")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void resetPassword(@Valid @RequestBody ResetPasswordRequest request) {
        passwordResetService.resetPassword(request.getToken(), request.getNewPassword());
    }
}
