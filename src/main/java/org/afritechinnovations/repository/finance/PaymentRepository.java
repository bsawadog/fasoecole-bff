package org.afritechinnovations.repository.finance;

import org.afritechinnovations.model.finance.Payment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;

public interface PaymentRepository extends JpaRepository<Payment, Long> {

    List<Payment> findByInvoiceId(Long invoiceId);

    long countByReferenceStartingWith(String prefix);

    @Query("""
        SELECT s.school.name AS schoolName, SUM(p.amount) AS totalCollected
        FROM Payment p
        JOIN p.invoice i
        JOIN i.student s
        WHERE p.paymentDate BETWEEN :start AND :end
        GROUP BY s.school.name
        """)
    List<Object[]> findTotalCollectedBySchool(
            @Param("start") LocalDate start,
            @Param("end") LocalDate end);
}