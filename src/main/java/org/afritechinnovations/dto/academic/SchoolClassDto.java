package org.afritechinnovations.dto.academic;

import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SchoolClassDto {
    private Long id;
    private Long schoolId;
    private Long academicYearId;
    private Long levelId;
    private String name;
    private Integer capacity;
}
