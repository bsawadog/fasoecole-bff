package org.afritechinnovations.dto.people;

import lombok.*;

import java.time.LocalDate;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TeacherDto {
    private Long id;
    private Long userId;
    private Long schoolId;
    private String specialty;
    private LocalDate hireDate;
}