package org.afritechinnovations.dto.communication;

import lombok.*;

import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AuditLogDto {
    private Long id;
    private Long userId;
    private String action;
    private String entity;
    private Long entityId;
    private LocalDateTime createdAt;
}
