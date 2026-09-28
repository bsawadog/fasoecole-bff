package org.afritechinnovations.dto.academic;

import lombok.*;

import java.math.BigDecimal;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ClassSubjectTeacherDto {
    private Long id;
    private Long classId;
    private Long subjectId;
    private Long teacherId;
    private BigDecimal coefficient;
}
