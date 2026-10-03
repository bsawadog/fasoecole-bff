package org.afritechinnovations.model.communication;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "conversation_attachment_content")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class ConversationAttachmentContent {
    @Id
    private Long id;
    @Column(nullable = false, columnDefinition = "bytea")
    private byte[] data;
}
