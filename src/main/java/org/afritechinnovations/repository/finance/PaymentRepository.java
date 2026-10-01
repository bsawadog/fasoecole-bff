package org.afritechinnovations.repository.finance;

import org.afritechinnovations.model.finance.Payment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;

public interface PaymentRepository extends JpaRepository<Payment, Long> {

    List<Payment> findByInvoiceId(Long invoiceId);

    @Query("""
        SELECT p FROM Payment p
        JOIN FETCH p.invoice i
        JOIN FETCH i.student s
        JOIN FETCH s.user u
        JOIN FETCH i.feeType ft
        WHERE s.school.id = :schoolId
        ORDER BY p.paymentDate DESC, p.id DESC
        """)
    List<Payment> findAllWithDetailsBySchoolId(@Param("schoolId") Long schoolId);

    long countByReferenceStartingWith(String prefix);

    /** Encaissements des frais scolaires sur la période : [date, montant]. */
    @Query("""
        SELECT p.paymentDate, p.amount FROM Payment p
        JOIN p.invoice i
        JOIN i.student s
        WHERE s.school.id = :schoolId AND p.paymentDate BETWEEN :from AND :to
        """)
    List<Object[]> findAmountsForSchoolBetween(@Param("schoolId") Long schoolId, @Param("from") LocalDate from,
                                               @Param("to") LocalDate to);

    boolean existsByReference(String reference);

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