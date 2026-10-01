package org.afritechinnovations.model.academic;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** Journal immuable des créations / modifications / suppressions de notes. */
@Entity
@Table(name = "grade_history")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class GradeHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "evaluation_id")
    private Long evaluationId;

    @Column(name = "evaluation_title", nullable = false, length = 160)
    private String evaluationTitle;

    @Column(name = "class_id")
    private Long classId;

    @Column(name = "period_id")
    private Long periodId;

    @Column(name = "student_id")
    private Long studentId;

    @Column(nullable = false, length = 20)
    private String action;

    @Column(name = "old_value", precision = 5, scale = 2)
    private BigDecimal oldValue;

    @Column(name = "new_value", precision = 5, scale = 2)
    private BigDecimal newValue;

    @Column(length = 255)
    private String reason;

    @Column(name = "changed_by")
    private Long changedBy;

    @Column(name = "changed_by_name", length = 160)
    private String changedByName;

    @Column(name = "changed_at", nullable = false)
    private LocalDateTime changedAt;
}