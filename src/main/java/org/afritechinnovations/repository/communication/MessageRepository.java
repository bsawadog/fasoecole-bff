package org.afritechinnovations.repository.communication;

import org.afritechinnovations.model.communication.Message;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface MessageRepository extends JpaRepository<Message, Long> {

    @Query("""
        SELECT m FROM Message m
        JOIN FETCH m.sender s
        WHERE m.receiver.id = :receiverId
        ORDER BY m.sentAt DESC
        """)
    List<Message> findInboxByReceiverId(@Param("receiverId") Long receiverId);

    List<Message> findBySenderIdOrderBySentAtDesc(Long senderId);

    long countByReceiverIdAndReadAtIsNull(Long receiverId);
}