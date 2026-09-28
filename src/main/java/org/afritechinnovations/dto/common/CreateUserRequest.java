package org.afritechinnovations.dto.common;

import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CreateUserRequest {
    private String firstName;
    private String lastName;
    private String email;
    private String password;
    private String phone;
}
