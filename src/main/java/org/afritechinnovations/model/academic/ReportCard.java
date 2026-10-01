package org.afritechinnovations.model.academic;

import jakarta.persistence.*;
import lombok.*;
import org.afritechinnovations.model.people.Student;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "report_cards")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ReportCard {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "student_id", nullable = false)
    private Student student;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "academic_year_id", nullable = false)
    private AcademicYear academicYear;

    @Column(nullable = false, length = 20)
    private String term;

    @Column(precision = 5, scale = 2)
    private BigDecimal average;

    private Integer rank;

    @Column(length = 255)
    private String comment;

    @Builder.Default
    @Column(nullable = false)
    private Boolean validated = false;

    @Column(name = "class_id")
    private Long classId;

    @Column(name = "period_id")
    private Long periodId;

    @Column(name = "class_size")
    private Integer classSize;

    @Column(length = 40)
    private String mention;

    @Column(name = "generated_at")
    private java.time.LocalDateTime generatedAt;
}