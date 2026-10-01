package org.afritechinnovations.model.finance;

import jakarta.persistence.*;
import lombok.*;
import org.afritechinnovations.model.common.School;

import java.time.LocalDateTime;

@Entity
@Table(name = "expense_categories")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ExpenseCategory {

    /** Catégorie alimentée automatiquement par les paiements de la paie des enseignants. */
    public static final String PAYROLL = "PAYROLL";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "school_id", nullable = false)
    private School school;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(length = 255)
    private String description;

    @Column(name = "system_code", length = 30)
    private String systemCode;

    @Builder.Default
    @Column(nullable = false)
    private boolean active = true;

    @Builder.Default
    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    public boolean isPayroll() {
        return PAYROLL.equals(systemCode);
    }
}
