package org.afritechinnovations.model.common;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "users")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class User {

    /** Allows preparing only schools actually owned by this user; never grants platform administration. */
    @Builder.Default
    @Column(name = "owner_account", nullable = false)
    private boolean ownerAccount = false;

    @Builder.Default
    @ElementCollection
    @CollectionTable(name = "user_platform_roles", joinColumns = @JoinColumn(name = "user_id"))
    @Column(name = "role", nullable = false, length = 30)
    private java.util.Set<String> platformRoles = new java.util.HashSet<>();

    @Builder.Default
    @Column(name = "must_change_password", nullable = false)
    private boolean mustChangePassword = false;

    @Builder.Default
    @ElementCollection
    @CollectionTable(name = "user_requested_children", joinColumns = @JoinColumn(name = "user_id"))
    @OrderColumn(name = "child_index")
    @Column(name = "registration_number", nullable = false, length = 50)
    private java.util.List<String> childRegistrationNumbers = new java.util.ArrayList<>();

    @Column(name = "school_identifier", length = 50)
    private String schoolIdentifier;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "first_name", nullable = false, length = 100)
    private String firstName;

    @Column(name = "last_name", nullable = false, length = 100)
    private String lastName;

    @Column(unique = true, length = 150)
    private String email;

    @Column(name = "password_hash", nullable = false)
    private String passwordHash;

    @Column(length = 30)
    private String phone;

    @Builder.Default
    @Column(nullable = false)
    private Boolean active = true;

    @Builder.Default
    @Column(nullable = false)
    private Boolean approved = true;

    /** Faux pour un compte créé par l'école dont le titulaire n'a pas encore choisi son mot de passe. */
    @Builder.Default
    @Column(name = "password_set", nullable = false)
    private Boolean passwordSet = true;

    /** Vrai une fois que le titulaire a prouvé qu'il possède son adresse (lien reçu par courriel). */
    @Builder.Default
    @Column(name = "email_verified", nullable = false)
    private Boolean emailVerified = false;

    @Builder.Default
    @Column(name = "session_version", nullable = false)
    private long sessionVersion = 0;
    public void revokeSessions() { sessionVersion++; }

    @Version
    @Column(name = "row_version", nullable = false)
    private Long rowVersion;

    @Column(name = "requested_school_id")
    private Long requestedSchoolId;

    @Enumerated(EnumType.STRING)
    @Column(name = "requested_role", length = 30)
    private RoleName requestedRole;

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
