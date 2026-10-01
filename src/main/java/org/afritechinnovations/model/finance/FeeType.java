package org.afritechinnovations.model.finance;

import jakarta.persistence.*;
import lombok.*;
import org.afritechinnovations.model.common.School;

import java.math.BigDecimal;

@Entity
@Table(name = "fee_types")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class FeeType {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "school_id", nullable = false)
    private School school;

    @Column(nullable = false, length = 150)
    private String name;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal amount;

    @Builder.Default
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private FeeFrequency frequency = FeeFrequency.ONE_TIME;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "level_id")
    private org.afritechinnovations.model.academic.Level level;

    @Column(length = 255)
    private String description;

    @Builder.Default
    @Column(nullable = false)
    private boolean active = true;
}