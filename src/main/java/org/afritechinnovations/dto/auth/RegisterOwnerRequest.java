package org.afritechinnovations.dto.auth;

import jakarta.validation.constraints.*;

/** No caller-supplied role or school ownership identifiers are accepted here. */
public record RegisterOwnerRequest(
        @NotBlank @Size(max=100) String firstName,
        @NotBlank @Size(max=100) String lastName,
        @NotBlank @Email @Size(max=150) String email,
        @Size(max=30) String phone,
        @NotBlank @Size(min=8,max=100) String password) { }
