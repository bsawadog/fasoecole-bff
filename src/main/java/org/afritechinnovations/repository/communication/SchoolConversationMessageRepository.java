package org.afritechinnovations.repository.communication;

import org.afritechinnovations.model.communication.SchoolConversationMessage;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface SchoolConversationMessageRepository extends JpaRepository<SchoolConversationMessage, Long> {

    @Query("""
        SELECT m FROM SchoolConversationMessage m
        LEFT JOIN FETCH m.sender
        WHERE m.conversation.id = :conversationId
        ORDER BY m.sentAt ASC, m.id ASC
        """)
    List<SchoolConversationMessage> findThread(@Param("conversationId") Long conversationId);

    @Query("SELECT COUNT(m) FROM SchoolConversationMessage m WHERE m.fromSchool = false AND EXISTS (" +
            "SELECT p.id FROM ConversationParticipant p WHERE p.conversation = m.conversation " +
            "AND p.school.id = :schoolId AND (p.lastReadAt IS NULL OR m.sentAt > p.lastReadAt))")
    long countUnreadBySchool(@Param("schoolId") Long schoolId);

    @Query("SELECT COUNT(m) FROM SchoolConversationMessage m WHERE EXISTS (" +
            "SELECT p.id FROM ConversationParticipant p WHERE p.conversation = m.conversation " +
            "AND p.user.id = :userId AND (p.lastReadAt IS NULL OR m.sentAt > p.lastReadAt)) " +
            "AND (m.sender IS NULL OR m.sender.id <> :userId)")
    long countUnreadByUser(@Param("userId") Long userId);
}
