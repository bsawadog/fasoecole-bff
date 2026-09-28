package org.afritechinnovations.model.academic;

import jakarta.persistence.*;
import lombok.*;
import org.afritechinnovations.model.people.Teacher;

import java.math.BigDecimal;

@Entity
@Table(name = "class_subject_teacher",
        uniqueConstraints = @UniqueConstraint(columnNames = {"class_id", "subject_id", "teacher_id"}))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ClassSubjectTeacher {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "class_id", nullable = false)
    private SchoolClass schoolClass;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "subject_id", nullable = false)
    private Subject subject;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "teacher_id", nullable = false)
    private Teacher teacher;

    @Builder.Default
    @Column(nullable = false, precision = 4, scale = 2)
    private BigDecimal coefficient = BigDecimal.ONE;
}