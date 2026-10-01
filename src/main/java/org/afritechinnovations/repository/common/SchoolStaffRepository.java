package org.afritechinnovations.repository.common;

import org.afritechinnovations.model.common.SchoolStaff;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface SchoolStaffRepository extends JpaRepository<SchoolStaff, Long> {

    Optional<SchoolStaff> findBySchoolIdAndUserId(Long schoolId, Long userId);

    boolean existsBySchoolIdAndUserId(Long schoolId, Long userId);

    @Query("""
        SELECT s FROM SchoolStaff s
        JOIN FETCH s.user u
        WHERE s.school.id = :schoolId
        ORDER BY s.active DESC, u.lastName, u.firstName
        """)
    List<SchoolStaff> findBySchoolWithUser(@Param("schoolId") Long schoolId);

    @Query("""
        SELECT s FROM SchoolStaff s
        JOIN FETCH s.school sc
        WHERE s.user.id = :userId
        ORDER BY sc.name
        """)
    List<SchoolStaff> findByUserWithSchool(@Param("userId") Long userId);
}
