package org.afritechinnovations.service.communication;

import lombok.RequiredArgsConstructor;
import org.afritechinnovations.dto.communication.MessageDto;
import org.afritechinnovations.model.common.User;
import org.afritechinnovations.model.communication.Message;
import org.afritechinnovations.repository.communication.MessageRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional
public class MessageService {

    private final MessageRepository messageRepository;

    public List<MessageDto> findInbox(Long receiverId) {
        return messageRepository.findInboxByReceiverId(receiverId)
                .stream()
                .map(this::toDto)
                .toList();
    }

    public List<MessageDto> findSent(Long senderId) {
        return messageRepository.findBySenderIdOrderBySentAtDesc(senderId)
                .stream()
                .map(this::toDto)
                .toList();
    }

    public long countUnread(Long receiverId) {
        return messageRepository.countByReceiverIdAndReadAtIsNull(receiverId);
    }

    public MessageDto findById(Long id) {
        Message message = messageRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Message introuvable: " + id));
        return toDto(message);
    }

    public MessageDto send(MessageDto dto) {
        Message message = Message.builder()
                .sender(User.builder().id(dto.getSenderId()).build())
                .receiver(User.builder().id(dto.getReceiverId()).build())
                .subject(dto.getSubject())
                .content(dto.getContent())
                .build();
        return toDto(messageRepository.save(message));
    }

    public MessageDto markAsRead(Long id) {
        Message message = messageRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Message introuvable: " + id));
        message.setReadAt(LocalDateTime.now());
        return toDto(messageRepository.save(message));
    }

    public void delete(Long id) {
        messageRepository.deleteById(id);
    }

    private MessageDto toDto(Message message) {
        return MessageDto.builder()
                .id(message.getId())
                .senderId(message.getSender().getId())
                .receiverId(message.getReceiver().getId())
                .subject(message.getSubject())
                .content(message.getContent())
                .sentAt(message.getSentAt())
                .readAt(message.getReadAt())
                .build();
    }
}
