package org.afritechinnovations.dto.people;

import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;

@Getter
@Setter
public class CreateStudentInvoiceRequest {

    @NotNull(message = "Le type de frais est obligatoire")
    private Long feeTypeId;

    private BigDecimal amountDue;

    @NotNull(message = "La date d'échéance est obligatoire")
    private LocalDate dueDate;

    private Long academicYearId;
}
