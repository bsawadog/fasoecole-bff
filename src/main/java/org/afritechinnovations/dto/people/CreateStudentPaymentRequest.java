package org.afritechinnovations.dto.people;

import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;
import org.afritechinnovations.model.finance.PaymentMethod;

import java.math.BigDecimal;
import java.time.LocalDate;

@Getter
@Setter
public class CreateStudentPaymentRequest {

    @NotNull(message = "Le montant est obligatoire")
    private BigDecimal amount;

    private LocalDate paymentDate;

    @NotNull(message = "Le mode de paiement est obligatoire")
    private PaymentMethod method;

    private String reference;
}
