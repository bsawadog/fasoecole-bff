package org.afritechinnovations.repository.people;

import org.afritechinnovations.model.people.TeacherPayment;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;

public interface TeacherPaymentRepository extends JpaRepository<TeacherPayment, Long> {

    List<TeacherPayment> findByTeacherIdAndPayMonthOrderByPaymentDateAscIdAsc(Long teacherId, LocalDate payMonth);

    List<TeacherPayment> findByTeacherIdAndPayMonthGreaterThanEqual(Long teacherId, LocalDate payMonth);

    boolean existsByTeacherIdAndPayMonthGreaterThanEqual(Long teacherId, LocalDate payMonth);
}