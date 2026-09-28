package org.afritechinnovations.dto.communication;

import lombok.*;

import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class NotificationDto {
    private Long id;
    private Long userId;
    private String title;
    private String content;
    private Boolean isRead;
    private LocalDateTime createdAt;
}
