package org.afritechinnovations.service.communication;
/** Same visibility and participant precedence as ConversationMessagingService.conversations(user, school). */
public final class UnreadMessageSql {
    private UnreadMessageSql() {}
    public static final String SCHOOL_BODY = """
        SELECT COUNT(*) FROM school_conversation_messages m
        JOIN school_conversations c ON c.id=m.conversation_id CROSS JOIN params scope
        WHERE c.school_id=scope.school_id AND (m.sender_id IS NULL OR m.sender_id<>scope.user_id)
          AND (
            EXISTS(SELECT 1 FROM conversation_participants p WHERE p.conversation_id=c.id AND p.user_id=scope.user_id
                AND (p.last_read_at IS NULL OR m.sent_at>p.last_read_at))
            OR (NOT EXISTS(SELECT 1 FROM conversation_participants personal WHERE personal.conversation_id=c.id AND personal.user_id=scope.user_id)
                AND m.from_school=FALSE AND EXISTS(SELECT 1 FROM conversation_participants p WHERE p.conversation_id=c.id AND p.school_id=scope.school_id
                    AND (p.last_read_at IS NULL OR m.sent_at>p.last_read_at)))
          )
        """;
}
