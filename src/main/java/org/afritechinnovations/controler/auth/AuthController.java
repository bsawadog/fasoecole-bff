package org.afritechinnovations.controler.auth;

import lombok.RequiredArgsConstructor;
import org.afritechinnovations.dto.auth.LoginRequest;
import org.afritechinnovations.dto.auth.LoginResponse;
import org.afritechinnovations.dto.common.CreateUserRequest;
import org.afritechinnovations.dto.common.UserDto;
import org.afritechinnovations.security.JwtService;
import org.afritechinnovations.security.UserPrincipal;
import org.afritechinnovations.service.common.UserService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthenticationManager authenticationManager;
    private final JwtService jwtService;
    private final UserService userService;

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
        } catch (BadCredentialsException ex) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
    }

    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    public UserDto register(@RequestBody CreateUserRequest request) {
        return userService.create(request);
    }
}
