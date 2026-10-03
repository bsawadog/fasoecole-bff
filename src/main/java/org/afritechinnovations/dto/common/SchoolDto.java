package org.afritechinnovations.dto.common;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.*;
import org.afritechinnovations.model.common.SchoolStatus;
import org.afritechinnovations.model.common.SchoolType;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SchoolDto {
    private Long id;
    @NotBlank(message = "Le nom de l'établissement est obligatoire")
    @Size(max = 200)
    private String name;
    @NotNull(message = "Le type d'établissement est obligatoire")
    private SchoolType type;
    @Size(max = 255)
    private String address;
    @Size(max = 30)
    private String phone;
    @Email
    @Size(max = 150)
    private String email;
    private Long ownerId;
    private SchoolStatus status;
    private java.time.LocalDateTime submittedAt;
}
