package org.afritechinnovations.dto.auth;

import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class LoginRequest {
    @jakarta.validation.constraints.NotBlank
    @jakarta.validation.constraints.Email
    @jakarta.validation.constraints.Size(max = 150)
    private String email;
    @jakarta.validation.constraints.NotBlank
    @jakarta.validation.constraints.Size(max = 100)
    private String password;
}
