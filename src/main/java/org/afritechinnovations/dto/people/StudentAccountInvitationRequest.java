package org.afritechinnovations.dto.people;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** An explicit choice of login address, never inferred from a school's contact address. */
public record StudentAccountInvitationRequest(@NotBlank @Email @Size(max = 150) String email) { }
