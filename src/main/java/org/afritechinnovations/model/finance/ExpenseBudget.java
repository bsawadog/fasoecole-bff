package org.afritechinnovations.model.finance;

import jakarta.persistence.*;
import lombok.*;
import org.afritechinnovations.model.academic.AcademicYear;

import java.math.BigDecimal;

/** Montant prévu pour une catégorie de dépenses sur une année scolaire. */
@Entity
@Table(name = "expense_budgets")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ExpenseBudget {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "academic_year_id", nullable = false)
    private AcademicYear academicYear;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "category_id", nullable = false)
    private ExpenseCategory category;

    @Column(nullable = false, precision = 14, scale = 2)
    private BigDecimal amount;
}
