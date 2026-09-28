package org.afritechinnovations.dto.academic;

import lombok.*;

import java.time.LocalDate;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AcademicYearDto {
    private Long id;
    private Long schoolId;
    private String label;
    private LocalDate startDate;
    private LocalDate endDate;
    private Boolean isCurrent;
}
