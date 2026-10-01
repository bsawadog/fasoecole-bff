package org.afritechinnovations.controler.communication;

import lombok.RequiredArgsConstructor;
import org.afritechinnovations.dto.communication.MessageDto;
import org.afritechinnovations.security.AccessGuard;
import org.afritechinnovations.service.communication.MessageService;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/messages")
@RequiredArgsConstructor
public class MessageController {

    private final MessageService messageService;
    private final AccessGuard guard;

    @GetMapping("/inbox/{receiverId}")
    public List<MessageDto> getInbox(@PathVariable Long receiverId) {
        guard.requireSelfOrSuperAdmin(receiverId);
        return messageService.findInbox(receiverId);
    }

    @GetMapping("/sent/{senderId}")
    public List<MessageDto> getSent(@PathVariable Long senderId) {
        guard.requireSelfOrSuperAdmin(senderId);
        return messageService.findSent(senderId);
    }

    @GetMapping("/inbox/{receiverId}/unread-count")
    public long getUnreadCount(@PathVariable Long receiverId) {
        guard.requireSelfOrSuperAdmin(receiverId);
        return messageService.countUnread(receiverId);
    }

    @GetMapping("/{id}")
    public MessageDto getById(@PathVariable Long id) {
        MessageDto message = messageService.findById(id);
        requireParticipant(message);
        return message;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public MessageDto send(@RequestBody MessageDto dto) {
        if (dto.getReceiverId() == null) {
            throw new IllegalArgumentException("Le destinataire est obligatoire");
        }
        if (!guard.sharesSchoolWith(dto.getReceiverId())) {
            throw new AccessDeniedException("Vous ne pouvez écrire qu'aux membres de vos établissements");
        }
        dto.setSenderId(guard.currentUserId());
        return messageService.send(dto);
    }

    @PatchMapping("/{id}/read")
    public MessageDto markAsRead(@PathVariable Long id) {
        guard.requireSelfOrSuperAdmin(messageService.findById(id).getReceiverId());
        return messageService.markAsRead(id);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id) {
        requireParticipant(messageService.findById(id));
        messageService.delete(id);
    }

    private void requireParticipant(MessageDto message) {
        Long me = guard.currentUserId();
        if (!me.equals(message.getSenderId()) && !me.equals(message.getReceiverId()) && !guard.isSuperAdmin()) {
            throw new AccessDeniedException("Ce message ne vous concerne pas");
        }
    }
}
