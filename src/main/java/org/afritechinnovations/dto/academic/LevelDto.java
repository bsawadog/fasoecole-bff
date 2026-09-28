package org.afritechinnovations.dto.academic;

import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class LevelDto {
    private Long id;
    private Long schoolId;
    private String name;
    private String cycle;
    private Integer orderIndex;
}
