package org.afritechinnovations.service.communication;

import lombok.RequiredArgsConstructor;
import org.afritechinnovations.dto.communication.NotificationDto;
import org.afritechinnovations.model.communication.Notification;
import org.afritechinnovations.repository.communication.NotificationRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional
public class NotificationService {

    private final NotificationRepository notificationRepository;
    private final org.afritechinnovations.repository.common.UserRepository userRepository;

    public List<NotificationDto> findAllByUser(Long userId) {
        return notificationRepository.findByUserIdOrderByCreatedAtDesc(userId)
                .stream()
                .map(this::toDto)
                .toList();
    }

    public List<NotificationDto> findUnreadByUser(Long userId) {
        return notificationRepository.findByUserIdAndIsReadFalseOrderByCreatedAtDesc(userId)
                .stream()
                .map(this::toDto)
                .toList();
    }

    public NotificationDto findById(Long id) {
        Notification notification = notificationRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Notification introuvable: " + id));
        return toDto(notification);
    }

    public NotificationDto create(NotificationDto dto) {
        Notification notification = Notification.builder()
                .user(userRepository.getReferenceById(dto.getUserId()))
                .title(dto.getTitle())
                .content(dto.getContent())
                .isRead(false)
                .build();
        return toDto(notificationRepository.save(notification));
    }

    public NotificationDto markAsRead(Long id) {
        Notification notification = notificationRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Notification introuvable: " + id));
        notification.setIsRead(true);
        return toDto(notificationRepository.save(notification));
    }

    public void delete(Long id) {
        notificationRepository.deleteById(id);
    }

    private NotificationDto toDto(Notification notification) {
        return NotificationDto.builder()
                .id(notification.getId())
                .userId(notification.getUser().getId())
                .title(notification.getTitle())
                .content(notification.getContent())
                .isRead(notification.getIsRead())
                .createdAt(notification.getCreatedAt())
                .build();
    }
}
