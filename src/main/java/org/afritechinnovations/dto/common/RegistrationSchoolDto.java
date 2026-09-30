package org.afritechinnovations.dto.common;

import org.afritechinnovations.model.common.SchoolType;

public record RegistrationSchoolDto(Long id, String name, SchoolType type) {
}
