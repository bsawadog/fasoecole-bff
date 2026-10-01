package org.afritechinnovations.model.academic;

import jakarta.persistence.*;
import lombok.*;
import org.afritechinnovations.model.common.School;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "grade_periods")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class GradePeriod {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "school_id", nullable = false)
    private School school;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "academic_year_id", nullable = false)
    private AcademicYear academicYear;

    @Column(nullable = false, length = 20)
    private String code;

    @Column(nullable = false, length = 80)
    private String name;

    @Column(name = "start_date", nullable = false)
    private LocalDate startDate;

    @Column(name = "end_date", nullable = false)
    private LocalDate endDate;

    @Builder.Default
    @Column(name = "pass_mark", nullable = false, precision = 5, scale = 2)
    private BigDecimal passMark = BigDecimal.TEN;

    @Builder.Default
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private GradePeriodStatus status = GradePeriodStatus.OPEN;

    @Column(name = "published_at")
    private LocalDateTime publishedAt;

    public boolean isEditable() {
        return status == GradePeriodStatus.OPEN;
    }
}