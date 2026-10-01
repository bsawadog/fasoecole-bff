package org.afritechinnovations.dto.common;

import lombok.*;
import org.afritechinnovations.model.common.RoleName;
import org.afritechinnovations.model.common.SchoolAccessStatus;
import org.afritechinnovations.model.common.SchoolType;

import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SchoolAccessRequestDto {
    private Long id;
    private Long userId;
    private String firstName;
    private String lastName;
    private String email;
    private String phone;
    private Long schoolId;
    private String schoolName;
    private SchoolType schoolType;
    private RoleName requestedRole;
    private SchoolAccessStatus status;
    private LocalDateTime createdAt;
    private LocalDateTime decidedAt;
    /** Pour un accès parent : enfants inscrits dans l'établissement concerné. */
    @Builder.Default
    private java.util.List<String> children = java.util.List.of();
}
