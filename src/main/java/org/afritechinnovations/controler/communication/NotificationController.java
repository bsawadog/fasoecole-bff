package org.afritechinnovations.controler.communication;

import lombok.RequiredArgsConstructor;
import org.afritechinnovations.dto.communication.NotificationDto;
import org.afritechinnovations.security.AccessGuard;
import org.afritechinnovations.service.communication.NotificationService;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/notifications")
@RequiredArgsConstructor
public class NotificationController {

    private final NotificationService notificationService;
    private final AccessGuard guard;

    @GetMapping("/by-user/{userId}")
    public List<NotificationDto> getAllByUser(@PathVariable Long userId) {
        guard.requireSelfOrSuperAdmin(userId);
        return notificationService.findAllByUser(userId);
    }

    @GetMapping("/by-user/{userId}/unread")
    public List<NotificationDto> getUnreadByUser(@PathVariable Long userId) {
        guard.requireSelfOrSuperAdmin(userId);
        return notificationService.findUnreadByUser(userId);
    }

    @GetMapping("/{id}")
    public NotificationDto getById(@PathVariable Long id) {
        NotificationDto notification = notificationService.findById(id);
        guard.requireSelfOrSuperAdmin(notification.getUserId());
        return notification;
    }

    /** Un propriétaire peut notifier les utilisateurs de ses établissements. */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public NotificationDto create(@RequestBody NotificationDto dto) {
        if (dto.getUserId() == null) {
            throw new IllegalArgumentException("Le destinataire est obligatoire");
        }
        guard.requireUserManager(dto.getUserId());
        return notificationService.create(dto);
    }

    @PatchMapping("/{id}/read")
    public NotificationDto markAsRead(@PathVariable Long id) {
        guard.requireSelfOrSuperAdmin(notificationService.findById(id).getUserId());
        return notificationService.markAsRead(id);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id) {
        guard.requireSelfOrSuperAdmin(notificationService.findById(id).getUserId());
        notificationService.delete(id);
    }
}
