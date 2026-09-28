package org.afritechinnovations.dto.academic;

import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class GradeDto {
    private Long id;
    private Long studentId;
    private Long classSubjectTeacherId;
    private String term;
    private String type;
    private BigDecimal value;
    private BigDecimal maxValue;
    private LocalDate gradeDate;
}
