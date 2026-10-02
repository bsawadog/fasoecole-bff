package org.afritechinnovations.controler.communication;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.afritechinnovations.dto.communication.FamilyContactDto;
import org.afritechinnovations.security.AccessGuard;
import org.afritechinnovations.service.communication.ConversationMessagingService;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/conversations")
@RequiredArgsConstructor
public class ConversationMessagingController {
    private final ConversationMessagingService service;
    private final AccessGuard guard;

    @GetMapping
    public List<FamilyContactDto.ConversationSummary> list(@RequestParam(required = false) Long schoolId) {
        return service.conversations(guard.currentUserId(), schoolId);
    }

    @GetMapping("/recipients")
    public List<FamilyContactDto.Recipient> recipients(@RequestParam Long schoolId,
                                                        @RequestParam(required = false) Long studentId) {
        return service.recipients(guard.currentUserId(), schoolId, studentId);
    }

    @GetMapping("/unread-count")
    public long unreadCount() { return service.unreadCount(guard.currentUserId()); }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public FamilyContactDto.ConversationThread start(@Valid @RequestBody FamilyContactDto.NewConversationRequest request) {
        return service.start(guard.currentUserId(), request);
    }

    @GetMapping("/{conversationId}")
    public FamilyContactDto.ConversationThread read(@PathVariable Long conversationId) {
        return service.read(guard.currentUserId(), conversationId);
    }

    @PostMapping("/{conversationId}/messages")
    public FamilyContactDto.ConversationThread reply(@PathVariable Long conversationId,
                                                      @Valid @RequestBody FamilyContactDto.ReplyRequest request) {
        return service.reply(guard.currentUserId(), conversationId, request);
    }
}
