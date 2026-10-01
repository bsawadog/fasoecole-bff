package org.afritechinnovations.controler.communication;

import lombok.RequiredArgsConstructor;
import org.afritechinnovations.dto.communication.SubscriptionDto;
import org.afritechinnovations.model.communication.SubscriptionStatus;
import org.afritechinnovations.security.AccessGuard;
import org.afritechinnovations.service.communication.SubscriptionService;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/subscriptions")
@RequiredArgsConstructor
public class SubscriptionController {

    private final SubscriptionService subscriptionService;
    private final AccessGuard guard;

    @GetMapping
    public List<SubscriptionDto> getBySchool(@RequestParam Long schoolId) {
        guard.requireOwnedSchool(schoolId);
        return subscriptionService.findBySchool(schoolId);
    }

    @GetMapping("/expiring-soon")
    public List<SubscriptionDto> getExpiringSoon(@RequestParam(defaultValue = "30") int daysAhead) {
        guard.requireSuperAdmin();
        return subscriptionService.findExpiringSoon(daysAhead);
    }

    @GetMapping("/{id}")
    public SubscriptionDto getById(@PathVariable Long id) {
        SubscriptionDto subscription = subscriptionService.findById(id);
        guard.requireOwnedSchool(subscription.getSchoolId());
        return subscription;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public SubscriptionDto create(@RequestBody SubscriptionDto dto) {
        guard.requireSuperAdmin();
        return subscriptionService.create(dto);
    }

    @PatchMapping("/{id}/status")
    public SubscriptionDto updateStatus(@PathVariable Long id, @RequestParam SubscriptionStatus status) {
        guard.requireSuperAdmin();
        return subscriptionService.updateStatus(id, status);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id) {
        guard.requireSuperAdmin();
        subscriptionService.delete(id);
    }
}
