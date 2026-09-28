package org.afritechinnovations.dto.finance;

import lombok.*;
import org.afritechinnovations.model.finance.FeeFrequency;

import java.math.BigDecimal;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class FeeTypeDto {
    private Long id;
    private Long schoolId;
    private String name;
    private BigDecimal amount;
    private FeeFrequency frequency;
}
