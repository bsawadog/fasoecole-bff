package org.afritechinnovations.model.people;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "parent_student",
        uniqueConstraints = @UniqueConstraint(columnNames = {"parent_id", "student_id"}))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ParentStudent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "parent_id", nullable = false)
    private Parent parent;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "student_id", nullable = false)
    private Student student;

    @Column(length = 50)
    private String relationship;
}