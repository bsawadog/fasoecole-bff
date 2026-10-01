package org.afritechinnovations.repository.people;

import org.afritechinnovations.model.people.TeacherPayment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;

public interface TeacherPaymentRepository extends JpaRepository<TeacherPayment, Long> {

    List<TeacherPayment> findByTeacherIdAndPayMonthOrderByPaymentDateAscIdAsc(Long teacherId, LocalDate payMonth);

    List<TeacherPayment> findByTeacherIdAndPayMonthGreaterThanEqual(Long teacherId, LocalDate payMonth);

    boolean existsByTeacherIdAndPayMonthGreaterThanEqual(Long teacherId, LocalDate payMonth);

    /** Paie effectivement versée par l'établissement sur la période (date de paiement). */
    @Query("""
        SELECT tp FROM TeacherPayment tp
        JOIN FETCH tp.teacher t
        JOIN FETCH t.user
        WHERE t.school.id = :schoolId AND tp.paymentDate BETWEEN :from AND :to
        ORDER BY tp.paymentDate DESC, tp.id DESC
        """)
    List<TeacherPayment> findForSchoolBetween(@Param("schoolId") Long schoolId, @Param("from") LocalDate from,
                                              @Param("to") LocalDate to);
}