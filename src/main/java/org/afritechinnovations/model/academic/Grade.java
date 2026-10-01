package org.afritechinnovations.model.academic;

import jakarta.persistence.*;
import lombok.*;
import org.afritechinnovations.model.people.Student;

import java.math.BigDecimal;
import java.time.LocalDate;

@Entity
@Table(name = "grades")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Grade {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "student_id", nullable = false)
    private Student student;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "class_subject_teacher_id", nullable = false)
    private ClassSubjectTeacher classSubjectTeacher;

    /** Null pour les notes historiques saisies avant le module d'évaluations. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "evaluation_id")
    private Evaluation evaluation;

    @Column(nullable = false, length = 20)
    private String term;

    @Column(nullable = false, length = 30)
    private String type;

    @Column(nullable = false, precision = 5, scale = 2)
    private BigDecimal value;

    @Builder.Default
    @Column(name = "max_value", nullable = false, precision = 5, scale = 2)
    private BigDecimal maxValue = BigDecimal.valueOf(20);

    @Builder.Default
    @Column(name = "grade_date", nullable = false)
    private LocalDate gradeDate = LocalDate.now();
}