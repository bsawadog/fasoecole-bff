package org.afritechinnovations.service.people;

import lombok.RequiredArgsConstructor;
import org.afritechinnovations.dto.people.ParentDto;
import org.afritechinnovations.model.common.User;
import org.afritechinnovations.model.people.Parent;
import org.afritechinnovations.repository.people.ParentRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional
public class ParentService {

    private final ParentRepository parentRepository;

    public ParentDto findById(Long id) {
        Parent parent = parentRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Parent introuvable: " + id));
        return toDto(parent);
    }

    public ParentDto findByUserId(Long userId) {
        Parent parent = parentRepository.findByUserId(userId)
                .orElseThrow(() -> new IllegalArgumentException("Parent introuvable pour user: " + userId));
        return toDto(parent);
    }

    public ParentDto create(ParentDto dto) {
        Parent parent = Parent.builder()
                .user(User.builder().id(dto.getUserId()).build())
                .build();
        return toDto(parentRepository.save(parent));
    }

    public void delete(Long id) {
        parentRepository.deleteById(id);
    }

    private ParentDto toDto(Parent parent) {
        return ParentDto.builder()
                .id(parent.getId())
                .userId(parent.getUser().getId())
                .build();
    }
}