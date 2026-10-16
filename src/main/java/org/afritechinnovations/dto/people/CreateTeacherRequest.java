package org.afritechinnovations.dto.people;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;

@Getter
@Setter
public class CreateTeacherRequest {

    @NotBlank(message = "Le prénom est obligatoire")
    @Size(max = 100)
    private String firstName;

    @NotBlank(message = "Le nom est obligatoire")
    @Size(max = 100)
    private String lastName;

    @Email(message = "Courriel invalide")
    @Size(max = 150)
    private String email;

    /** Ancien champ conservé pour compatibilité ; jamais utilisé pour définir le mot de passe. */
    @Size(max = 100)
    private String password;

    @Size(max = 30)
    private String phone;

    @Size(max = 150)
    private String specialty;

    @Size(max = 50)
    private String employeeNumber;

    private LocalDate hireDate;

    @jakarta.validation.constraints.DecimalMin("0.00")
    @jakarta.validation.constraints.Digits(integer = 10, fraction = 2)
    private java.math.BigDecimal monthlySalary;

    @jakarta.validation.constraints.Pattern(regexp = "FIXED_MONTHLY|HOURLY")
    private String rateType;

    @jakarta.validation.constraints.DecimalMin("0.01")
    @jakarta.validation.constraints.Digits(integer = 10, fraction = 2)
    private java.math.BigDecimal hourlyRate;

    private LocalDate rateEffectiveFrom;

    /** Classe facultative ; les matières restent enregistrées sans affectation. */
    private Long classId;

    @Size(max = 100)
    private java.util.List<@NotNull Long> subjectIds = new java.util.ArrayList<>();

    @jakarta.validation.Valid
    @Size(max = 100)
    private java.util.List<InitialTeacherSlotRequest> initialSlots = new java.util.ArrayList<>();


}
