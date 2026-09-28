package org.afritechinnovations.dto.communication;

import lombok.*;

import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MessageDto {
    private Long id;
    private Long senderId;
    private Long receiverId;
    private String subject;
    private String content;
    private LocalDateTime sentAt;
    private LocalDateTime readAt;
}
