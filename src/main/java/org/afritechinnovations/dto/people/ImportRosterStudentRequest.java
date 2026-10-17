package org.afritechinnovations.dto.people;

import jakarta.validation.constraints.*;
import lombok.Getter;
import lombok.Setter;
import org.afritechinnovations.model.finance.PaymentMethod;
import java.math.BigDecimal;
import java.time.LocalDate;

@Getter @Setter
public class ImportRosterStudentRequest extends CreateRosterStudentRequest {
    @jakarta.validation.Valid
    private CreateRosterParentRequest parent;
    @Size(max=150) private String feeTypeName;
    @DecimalMin("0.00") @Digits(integer=10,fraction=2) private BigDecimal fees;
    @DecimalMin("0.00") @Digits(integer=10,fraction=2) private BigDecimal paid;
    private LocalDate dueDate;
    private LocalDate paymentDate;
    private PaymentMethod paymentMethod;
}
