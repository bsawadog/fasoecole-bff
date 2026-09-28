package org.afritechinnovations.dto.academic;

import lombok.*;

import java.math.BigDecimal;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ReportCardDto {
    private Long id;
    private Long studentId;
    private Long academicYearId;
    private String term;
    private BigDecimal average;
    private Integer rank;
    private String comment;
    private Boolean validated;
}
