package org.afritechinnovations.model.common;
import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity @Table(name="account_recovery_events")
@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
public class AccountRecoveryEvent {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
    @ManyToOne(fetch=FetchType.LAZY) @JoinColumn(name="request_id",nullable=false) private AccountRecoveryRequest request;
    private Long actorId;
    @Column(nullable=false,length=40) private String action;
    @Column(length=1000) private String details;
    @Column(nullable=false) private LocalDateTime createdAt;
}
