package org.afritechinnovations.controler.communication;

import lombok.RequiredArgsConstructor;
import org.afritechinnovations.dto.communication.MessageDto;
import org.afritechinnovations.service.communication.MessageService;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/messages")
@RequiredArgsConstructor
public class MessageController {

    private final MessageService messageService;

    @GetMapping("/inbox/{receiverId}")
    public List<MessageDto> getInbox(@PathVariable Long receiverId) {
        return messageService.findInbox(receiverId);
    }

    @GetMapping("/sent/{senderId}")
    public List<MessageDto> getSent(@PathVariable Long senderId) {
        return messageService.findSent(senderId);
    }

    @GetMapping("/inbox/{receiverId}/unread-count")
    public long getUnreadCount(@PathVariable Long receiverId) {
        return messageService.countUnread(receiverId);
    }

    @GetMapping("/{id}")
    public MessageDto getById(@PathVariable Long id) {
        return messageService.findById(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public MessageDto send(@RequestBody MessageDto dto) {
        return messageService.send(dto);
    }

    @PatchMapping("/{id}/read")
    public MessageDto markAsRead(@PathVariable Long id) {
        return messageService.markAsRead(id);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id) {
        messageService.delete(id);
    }
}
