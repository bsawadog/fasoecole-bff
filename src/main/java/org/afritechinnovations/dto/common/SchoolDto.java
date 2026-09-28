package org.afritechinnovations.dto.common;

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
    private String name;
    private SchoolType type;
    private String address;
    private String phone;
    private String email;
    private Long ownerId;
    private SchoolStatus status;
}
