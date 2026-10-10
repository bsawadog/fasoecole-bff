package org.afritechinnovations.model.people;

import jakarta.persistence.*;
import lombok.*;
import org.afritechinnovations.model.common.School;
import org.afritechinnovations.model.common.User;

import java.time.LocalDate;

@Entity
@Table(name = "teachers")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Teacher {

    @Column(name = "employee_number", nullable = false, length = 50)
    private String employeeNumber;

    @PrePersist
    protected void assignEmployeeNumber() {
        if (employeeNumber == null || employeeNumber.isBlank()) employeeNumber = "EMP-" + java.util.UUID.randomUUID();
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "monthly_salary", precision = 12, scale = 2)
    private java.math.BigDecimal monthlySalary;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "school_id", nullable = false)
    private School school;

    @Column(length = 150)
    private String specialty;

    @Column(name = "hire_date")
    private LocalDate hireDate;
}