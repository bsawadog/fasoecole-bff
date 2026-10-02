package org.afritechinnovations.repository.communication;

import org.afritechinnovations.model.communication.ConversationParticipant;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface ConversationParticipantRepository extends JpaRepository<ConversationParticipant, Long> {
    @Query("SELECT p FROM ConversationParticipant p JOIN FETCH p.conversation c JOIN FETCH c.school " +
            "LEFT JOIN FETCH p.user WHERE p.user.id = :userId ORDER BY c.lastMessageAt DESC")
    List<ConversationParticipant> findAllForUser(@Param("userId") Long userId);

    List<ConversationParticipant> findByConversationId(Long conversationId);
    List<ConversationParticipant> findBySchoolId(Long schoolId);
    Optional<ConversationParticipant> findByConversationIdAndUserId(Long conversationId, Long userId);
    Optional<ConversationParticipant> findByConversationIdAndSchoolId(Long conversationId, Long schoolId);
    boolean existsByConversationIdAndUserId(Long conversationId, Long userId);

    @Query("SELECT COUNT(p) FROM ConversationParticipant p WHERE p.school.id = :schoolId " +
            "AND (p.lastReadAt IS NULL OR p.lastReadAt < p.conversation.lastMessageAt)")
    long countUnreadBySchool(@Param("schoolId") Long schoolId);
}
