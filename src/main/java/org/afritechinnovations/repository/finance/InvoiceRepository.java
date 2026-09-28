package org.afritechinnovations.repository.finance;

import org.afritechinnovations.model.finance.Invoice;
import org.afritechinnovations.model.finance.InvoiceStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;

public interface InvoiceRepository extends JpaRepository<Invoice, Long> {

    List<Invoice> findByStudentId(Long studentId);

    List<Invoice> findByStatusIn(List<InvoiceStatus> statuses);

    @Query("""
        SELECT i FROM Invoice i
        JOIN FETCH i.student s
        JOIN FETCH s.user u
        JOIN FETCH i.feeType ft
        WHERE i.status IN :statuses
        ORDER BY i.dueDate ASC
        """)
    List<Invoice> findAllWithDetailsByStatuses(@Param("statuses") List<InvoiceStatus> statuses);

    @Query("""
        SELECT i FROM Invoice i
        WHERE i.dueDate < :today AND i.status = 'PENDING'
        """)
    List<Invoice> findOverdueCandidates(@Param("today") LocalDate today);

    @Query("""
        SELECT i.id AS invoiceId,
               i.amountDue AS amountDue,
               COALESCE(SUM(p.amount), 0) AS totalPaid,
               (i.amountDue - COALESCE(SUM(p.amount), 0)) AS balance
        FROM Invoice i
        LEFT JOIN Payment p ON p.invoice = i
        WHERE i.student.id = :studentId
        GROUP BY i.id, i.amountDue
        """)
    List<Object[]> findBalanceByStudentId(@Param("studentId") Long studentId);
}