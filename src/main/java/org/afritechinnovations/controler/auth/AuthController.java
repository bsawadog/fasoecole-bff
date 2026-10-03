package org.afritechinnovations.controler.auth;

import lombok.RequiredArgsConstructor;
import org.afritechinnovations.dto.auth.LoginRequest;
import org.afritechinnovations.dto.auth.LoginResponse;
import org.afritechinnovations.dto.auth.ForgotPasswordRequest;
import org.afritechinnovations.dto.auth.ResetPasswordRequest;
import org.afritechinnovations.dto.auth.RegisterUserRequest;
import org.afritechinnovations.dto.auth.VerifyEmailRequest;
import org.afritechinnovations.service.auth.EmailVerificationService;
import org.afritechinnovations.security.CustomUserDetailsService;
import org.afritechinnovations.security.JwtService;
import org.afritechinnovations.security.UserPrincipal;
import org.afritechinnovations.service.auth.PasswordResetService;
import org.afritechinnovations.service.common.ParentAutoAccessService;
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
    private final ParentAutoAccessService parentAutoAccessService;
    private final EmailVerificationService emailVerificationService;
    private final CustomUserDetailsService userDetailsService;

    @PostMapping("/login")
    public ResponseEntity<LoginResponse> login(@Valid @RequestBody LoginRequest request) {
        try {
            var authentication = authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(request.getEmail().trim(), request.getPassword()));

            UserPrincipal principal = (UserPrincipal) authentication.getPrincipal();
            UserPrincipal authenticatedPrincipal = principal;
            // Parent reconnu par son courriel : accès automatique aux établissements de ses enfants.
            parentAutoAccessService.grantFromChildren(principal.getId(), false);
            principal = (UserPrincipal) userDetailsService.loadUserByUsername(principal.getEmail());
            // Une réinitialisation entre la vérification du mot de passe et l'émission du JWT
            // ne doit pas permettre d'ouvrir une nouvelle session avec les anciens identifiants.
            if (!principal.getId().equals(authenticatedPrincipal.getId())
                    || principal.getSessionVersion() != authenticatedPrincipal.getSessionVersion()
                    || !java.util.Objects.equals(principal.getPasswordHash(), authenticatedPrincipal.getPasswordHash())) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
            }
            String token = jwtService.generateToken(principal.getId(), principal.getEmail(), principal.getRoles(), principal.getSessionVersion());

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
    public ResponseEntity<?> register(@Valid @RequestBody RegisterUserRequest request) {
        userService.receiveRegistration(request);
        return ResponseEntity.accepted().body(Map.of("activationRequired", true,
                "message", "Votre demande a été reçue. Si cette adresse permet une inscription ou une activation, consultez votre courriel pour les instructions. Pour un compte existant, utilisez la connexion ou Mot de passe oublié."));
    }

    @PostMapping("/register-owner")
    public ResponseEntity<?> registerOwner(@Valid @RequestBody org.afritechinnovations.dto.auth.RegisterOwnerRequest request) {
        userService.receiveOwnerRegistration(request);
        return ResponseEntity.accepted().body(Map.of("activationRequired",true,"message",
                "Votre demande a été reçue. Pour une nouvelle inscription, consultez votre courriel afin de confirmer votre compte propriétaire. Si vous possédez déjà un compte, connectez-vous pour ajouter votre établissement."));
    }

    @PostMapping("/verify-email")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void verifyEmail(@Valid @RequestBody VerifyEmailRequest request) {
        emailVerificationService.confirm(request.getToken(), request.getNewPassword());
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
