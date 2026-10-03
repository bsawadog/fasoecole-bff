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
    @Builder.Default
    private java.util.List<String> childRegistrationNumbers = java.util.List.of();
    @Builder.Default
    private java.util.List<String> childReview = java.util.List.of();
    private String schoolIdentifier;
    private String identifierReview;
    private Long id;
    private String firstName;
    private String lastName;
    private String email;
    private String phone;
    private Boolean active;
    private Boolean approved;
    private Boolean emailVerified;
    private Boolean passwordSet;
    private boolean mustChangePassword;
    private boolean ownerAccount;
    private String invitationDeliveryStatus;
    private List<String> onboardingSteps;
    private Long requestedSchoolId;
    private String requestedSchoolName;
    private SchoolType requestedSchoolType;
    private RoleName requestedRole;
    private List<String> roles;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
