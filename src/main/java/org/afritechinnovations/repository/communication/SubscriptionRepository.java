package org.afritechinnovations.repository.communication;

import org.afritechinnovations.model.communication.Subscription;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;

public interface SubscriptionRepository extends JpaRepository<Subscription, Long> {

    List<Subscription> findBySchoolId(Long schoolId);

    @Query("""
        SELECT s FROM Subscription s
        JOIN FETCH s.school sc
        WHERE s.endDate BETWEEN :today AND :limitDate
        """)
    List<Subscription> findExpiringSoon(
            @Param("today") LocalDate today,
            @Param("limitDate") LocalDate limitDate);
}