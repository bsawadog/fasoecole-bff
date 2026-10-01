package org.afritechinnovations.model.academic;

import jakarta.persistence.*;
import lombok.*;
import org.afritechinnovations.model.common.School;

import java.math.BigDecimal;

@Entity
@Table(name = "subjects")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Subject {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "school_id", nullable = false)
    private School school;

    @Column(nullable = false, length = 150)
    private String name;

    @Column(length = 30)
    private String code;

    /** Coefficient par défaut, appliqué aux classes qui ne le surchargent pas. */
    @Builder.Default
    @Column(nullable = false, precision = 4, scale = 2)
    private BigDecimal coefficient = BigDecimal.ONE;
}