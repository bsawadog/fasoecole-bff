package org.afritechinnovations.model.common;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "school_access_requests")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SchoolAccessRequest {

    @Builder.Default
    @ElementCollection
    @CollectionTable(name = "school_access_requested_children", joinColumns = @JoinColumn(name = "request_id"))
    @OrderColumn(name = "child_index")
    @Column(name = "registration_number", nullable = false, length = 50)
    private java.util.List<String> childRegistrationNumbers = new java.util.ArrayList<>();

    @Column(name = "school_identifier", length = 50)
    private String schoolIdentifier;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "school_id", nullable = false)
    private School school;

    @Enumerated(EnumType.STRING)
    @Column(name = "requested_role", nullable = false, length = 30)
    private RoleName requestedRole;

    @Builder.Default
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private SchoolAccessStatus status = SchoolAccessStatus.PENDING;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "decided_by")
    private User decidedBy;

    @Column(name = "decided_at")
    private LocalDateTime decidedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }
}
