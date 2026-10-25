package org.afritechinnovations.model.common;

import jakarta.persistence.*;
import lombok.*;
import org.afritechinnovations.model.people.Student;
import java.time.LocalDateTime;

@Entity @Table(name="account_recovery_requests")
@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
public class AccountRecoveryRequest {
    public enum Status { PENDING, SUPPORT_REQUIRED, APPROVED, REJECTED, COMPLETED, SUPERSEDED }
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
    @ManyToOne(fetch=FetchType.LAZY) @JoinColumn(name="school_id",nullable=false) private School school;
    @ManyToOne(fetch=FetchType.LAZY) @JoinColumn(name="student_id") private Student student;
    @ManyToOne(fetch=FetchType.LAZY) @JoinColumn(name="user_id",nullable=false) private User user;
    @Column(nullable=false,length=100) private String firstName;
    @Column(nullable=false,length=100) private String lastName;
    @Column(nullable=false,length=50) private String registrationNumber;
    @Column(nullable=false,length=150) private String newEmail;
    @Column(nullable=false,length=1000) private String reason;
    @Enumerated(EnumType.STRING) @Column(nullable=false,length=30) private Status status;
    @Column(nullable=false) private LocalDateTime createdAt;
    private LocalDateTime decidedAt;
    private LocalDateTime completedAt;
    private Long decidedBy;
    private boolean supportDecision;
    @Column(length=1000) private String verificationNote;
    @Column(length=20) private String deliveryStatus;
    @Column(length=20) private String oldNotificationStatus;
    @Column(length=64,unique=true) private String tokenHash;
    private LocalDateTime expiresAt;
    @Column(length=150) private String previousEmail;
    private Long requestSessionVersion;
    private LocalDateTime lastSentAt;
    private Long lastSentBy;
    private int sendCount;
}
