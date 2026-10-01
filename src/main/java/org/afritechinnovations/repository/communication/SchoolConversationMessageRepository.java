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
}