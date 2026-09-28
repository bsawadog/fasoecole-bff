package org.afritechinnovations.dto.finance;

import lombok.*;
import org.afritechinnovations.model.finance.InvoiceStatus;

import java.math.BigDecimal;
import java.time.LocalDate;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class InvoiceDto {
    private Long id;
    private Long studentId;
    private Long feeTypeId;
    private Long academicYearId;
    private BigDecimal amountDue;
    private LocalDate dueDate;
    private InvoiceStatus status;
}
