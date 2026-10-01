package org.afritechinnovations.model.academic;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "evaluations")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Evaluation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "class_subject_teacher_id", nullable = false)
    private ClassSubjectTeacher classSubjectTeacher;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "period_id", nullable = false)
    private GradePeriod period;

    @Column(nullable = false, length = 120)
    private String title;

    @Column(nullable = false, length = 30)
    private String type;

    @Column(name = "eval_date", nullable = false)
    private LocalDate evalDate;

    @Builder.Default
    @Column(name = "max_value", nullable = false, precision = 5, scale = 2)
    private BigDecimal maxValue = BigDecimal.valueOf(20);

    @Builder.Default
    @Column(nullable = false, precision = 4, scale = 2)
    private BigDecimal weight = BigDecimal.ONE;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;
}