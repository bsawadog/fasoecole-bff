package org.afritechinnovations.dto.academic;

import lombok.*;

import java.math.BigDecimal;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SubjectDto {
    private Long id;
    private Long schoolId;
    private String name;
    private String code;
    private BigDecimal coefficient;
}
