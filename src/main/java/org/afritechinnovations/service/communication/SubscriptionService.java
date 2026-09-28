package org.afritechinnovations.service.communication;

import lombok.RequiredArgsConstructor;
import org.afritechinnovations.dto.communication.SubscriptionDto;
import org.afritechinnovations.model.common.School;
import org.afritechinnovations.model.communication.Subscription;
import org.afritechinnovations.model.communication.SubscriptionStatus;
import org.afritechinnovations.repository.communication.SubscriptionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional
public class SubscriptionService {

    private final SubscriptionRepository subscriptionRepository;

    public List<SubscriptionDto> findBySchool(Long schoolId) {
        return subscriptionRepository.findBySchoolId(schoolId)
                .stream()
                .map(this::toDto)
                .toList();
    }

    public List<SubscriptionDto> findExpiringSoon(int daysAhead) {
        LocalDate today = LocalDate.now();
        return subscriptionRepository.findExpiringSoon(today, today.plusDays(daysAhead))
                .stream()
                .map(this::toDto)
                .toList();
    }

    public SubscriptionDto findById(Long id) {
        Subscription subscription = subscriptionRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Abonnement introuvable: " + id));
        return toDto(subscription);
    }

    public SubscriptionDto create(SubscriptionDto dto) {
        Subscription subscription = Subscription.builder()
                .school(School.builder().id(dto.getSchoolId()).build())
                .plan(dto.getPlan())
                .startDate(dto.getStartDate())
                .endDate(dto.getEndDate())
                .status(dto.getStatus() != null ? dto.getStatus() : SubscriptionStatus.ACTIVE)
                .build();
        return toDto(subscriptionRepository.save(subscription));
    }

    public SubscriptionDto updateStatus(Long id, SubscriptionStatus status) {
        Subscription subscription = subscriptionRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Abonnement introuvable: " + id));
        subscription.setStatus(status);
        return toDto(subscriptionRepository.save(subscription));
    }

    public void delete(Long id) {
        subscriptionRepository.deleteById(id);
    }

    private SubscriptionDto toDto(Subscription subscription) {
        return SubscriptionDto.builder()
                .id(subscription.getId())
                .schoolId(subscription.getSchool().getId())
                .plan(subscription.getPlan())
                .startDate(subscription.getStartDate())
                .endDate(subscription.getEndDate())
                .status(subscription.getStatus())
                .build();
    }
}
