package org.afritechinnovations.dto.common;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ChangeEmailRequest(@NotBlank @Email @Size(max=150) String email,
                                 @NotBlank @Size(max=100) String currentPassword) { }
