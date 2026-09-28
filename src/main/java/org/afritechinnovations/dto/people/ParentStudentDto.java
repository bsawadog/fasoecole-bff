package org.afritechinnovations.dto.people;

import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ParentStudentDto {
    private Long id;
    private Long parentId;
    private Long studentId;
    private String relationship;
}
