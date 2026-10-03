package org.afritechinnovations.model.common;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "email_verification_tokens")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class EmailVerificationToken {

    public enum Purpose { VERIFY, ACTIVATE }

    @Builder.Default
    @ElementCollection
    @CollectionTable(name = "verification_requested_children", joinColumns = @JoinColumn(name = "token_id"))
    @OrderColumn(name = "child_index")
    @Column(name = "registration_number", nullable = false, length = 50)
    private java.util.List<String> childRegistrationNumbers = new java.util.ArrayList<>();

    @Column(name = "school_identifier", length = 50)
    private String schoolIdentifier;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false, unique = true)
    private User user;

    @Column(name = "token_hash", nullable = false, unique = true, length = 64)
    private String tokenHash;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Purpose purpose;

    @Column(name = "recipient_email", length = 150)
    private String recipientEmail;
    @Builder.Default
    @Column(name = "delivery_status", nullable = false, length = 20)
    private String deliveryStatus = "PENDING";

    /** Établissement choisi lors de la tentative d'inscription (prise en main d'un compte existant). */
    @Column(name = "requested_school_id")
    private Long requestedSchoolId;

    @Enumerated(EnumType.STRING)
    @Column(name = "requested_role", length = 30)
    private RoleName requestedRole;

    @Column(name = "expires_at", nullable = false)
    private LocalDateTime expiresAt;

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }
}
