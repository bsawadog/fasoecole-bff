package org.afritechinnovations.model.common;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.EnumSet;
import java.util.Set;

/** Membre du personnel administratif d'un établissement et modules qui lui sont délégués. */
@Entity
@Table(name = "school_staff")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SchoolStaff {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "monthly_salary", precision = 12, scale = 2)
    private java.math.BigDecimal monthlySalary;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "school_id", nullable = false)
    private School school;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "job_title", nullable = false, length = 80)
    private String jobTitle;

    @Builder.Default
    @Column(nullable = false)
    private boolean active = true;

    @Builder.Default
    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "school_staff_modules", joinColumns = @JoinColumn(name = "staff_id"))
    @Enumerated(EnumType.STRING)
    @Column(name = "module", length = 30, nullable = false)
    private Set<StaffModule> modules = EnumSet.noneOf(StaffModule.class);

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "created_by")
    private User createdBy;

    @Builder.Default
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    public boolean allows(StaffModule module) {
        return active && modules.contains(module);
    }
}
