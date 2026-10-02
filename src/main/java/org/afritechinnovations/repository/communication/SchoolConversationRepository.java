package org.afritechinnovations.repository.communication;

import org.afritechinnovations.model.communication.SchoolConversation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface SchoolConversationRepository extends JpaRepository<SchoolConversation, Long> {

    @Query("""
        SELECT c FROM SchoolConversation c
        JOIN FETCH c.school
        LEFT JOIN FETCH c.student s
        LEFT JOIN FETCH s.user
        WHERE c.parentUser.id = :userId
        ORDER BY c.lastMessageAt DESC
        """)
    List<SchoolConversation> findByParentUser(@Param("userId") Long userId);

    @Query("""
        SELECT c FROM SchoolConversation c
        LEFT JOIN FETCH c.parentUser
        LEFT JOIN FETCH c.student s
        LEFT JOIN FETCH s.user
        WHERE c.school.id = :schoolId
        ORDER BY c.lastMessageAt DESC
        """)
    List<SchoolConversation> findBySchool(@Param("schoolId") Long schoolId);

    long countBySchoolIdAndUnreadBySchoolTrue(Long schoolId);
}