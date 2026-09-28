package org.afritechinnovations.dto.finance;

import lombok.*;
import org.afritechinnovations.model.finance.PaymentMethod;

import java.math.BigDecimal;
import java.time.LocalDate;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PaymentDto {
    private Long id;
    private Long invoiceId;
    private BigDecimal amount;
    private LocalDate paymentDate;
    private PaymentMethod method;
    private String reference;
}
