package org.afritechinnovations.controler.communication;

import lombok.RequiredArgsConstructor;
import org.afritechinnovations.dto.communication.NotificationDto;
import org.afritechinnovations.service.communication.NotificationService;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/notifications")
@RequiredArgsConstructor
public class NotificationController {

    private final NotificationService notificationService;

    @GetMapping("/by-user/{userId}")
    public List<NotificationDto> getAllByUser(@PathVariable Long userId) {
        return notificationService.findAllByUser(userId);
    }

    @GetMapping("/by-user/{userId}/unread")
    public List<NotificationDto> getUnreadByUser(@PathVariable Long userId) {
        return notificationService.findUnreadByUser(userId);
    }

    @GetMapping("/{id}")
    public NotificationDto getById(@PathVariable Long id) {
        return notificationService.findById(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public NotificationDto create(@RequestBody NotificationDto dto) {
        return notificationService.create(dto);
    }

    @PatchMapping("/{id}/read")
    public NotificationDto markAsRead(@PathVariable Long id) {
        return notificationService.markAsRead(id);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id) {
        notificationService.delete(id);
    }
}
