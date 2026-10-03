package org.afritechinnovations.dto.people;

import java.time.LocalDate;
import java.util.List;

public record ClassRosterRowDto(
        Long studentId,
        Long userId,
        String firstName,
        String lastName,
        String email,
        String phone,
        String registrationNumber,
        LocalDate birthDate,
        String gender,
        List<ParentInfo> parents,
        List<String> teacherNames,
        Boolean emailVerified,
        String invitationDeliveryStatus
) {
    public record ParentInfo(
            Long parentId,
            Long userId,
            String firstName,
            String lastName,
            String email,
            String phone,
            String relationship
    ) {
    }
}
