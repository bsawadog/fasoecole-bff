package org.afritechinnovations.dto.academic;

import lombok.*;

import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DocumentDto {
    private Long id;
    private Long schoolId;
    private Long classId;
    private Long uploadedBy;
    private String title;
    private String fileUrl;
    private String type;
    private LocalDateTime createdAt;
}
