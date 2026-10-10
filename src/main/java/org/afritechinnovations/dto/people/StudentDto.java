package org.afritechinnovations.dto.people;

import lombok.*;

import java.time.LocalDate;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class StudentDto {
    private Long id;
    private Long userId;
    private Long schoolId;
    private String registrationNumber;
    private LocalDate birthDate;
    private String gender;
    private String firstName;
    private String lastName;
    private String email;
    private String phone;
}
