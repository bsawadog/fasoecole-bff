package org.afritechinnovations.dto.common;

public record StudentAdmissionRequest(Long classId,
        @jakarta.validation.constraints.Size(max = 50) String registrationNumber) { }
