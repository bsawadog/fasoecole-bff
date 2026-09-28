package org.afritechinnovations.dto.common;

import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SchoolUserDto {
    private Long id;
    private Long userId;
    private Long schoolId;
    private Long roleId;
}
