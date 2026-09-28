package org.afritechinnovations.repository.common;

import org.afritechinnovations.model.common.SchoolUser;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface SchoolUserRepository extends JpaRepository<SchoolUser, Long> {

    List<SchoolUser> findBySchoolId(Long schoolId);

    List<SchoolUser> findByUserId(Long userId);

    @Query("""
        SELECT su FROM SchoolUser su
        JOIN FETCH su.user u
        JOIN FETCH su.role r
        WHERE su.school.id = :schoolId
        ORDER BY u.lastName
        """)
    List<SchoolUser> findAllWithUserAndRoleBySchoolId(@Param("schoolId") Long schoolId);
}