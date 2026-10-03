package org.afritechinnovations.model.communication;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "conversation_attachments")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class ConversationAttachment {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "message_id", nullable = false)
    private SchoolConversationMessage message;
    @Column(nullable = false, length = 200)
    private String filename;
    @Column(name = "size_bytes", nullable = false)
    private long sizeBytes;
}
