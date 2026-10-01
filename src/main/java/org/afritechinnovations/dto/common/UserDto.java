package org.afritechinnovations.dto.common;

import lombok.*;
import org.afritechinnovations.model.common.RoleName;
import org.afritechinnovations.model.common.SchoolType;

import java.time.LocalDateTime;
import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UserDto {
    private Long id;
    private String firstName;
    private String lastName;
    private String email;
    private String phone;
    private Boolean active;
    private Boolean approved;
    private Boolean emailVerified;
    private Long requestedSchoolId;
    private String requestedSchoolName;
    private SchoolType requestedSchoolType;
    private RoleName requestedRole;
    private List<String> roles;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
