package org.afritechinnovations.controler.communication;

import lombok.RequiredArgsConstructor;
import org.afritechinnovations.dto.communication.SubscriptionDto;
import org.afritechinnovations.model.communication.SubscriptionStatus;
import org.afritechinnovations.service.communication.SubscriptionService;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/subscriptions")
@RequiredArgsConstructor
public class SubscriptionController {

    private final SubscriptionService subscriptionService;

    @GetMapping
    public List<SubscriptionDto> getBySchool(@RequestParam Long schoolId) {
        return subscriptionService.findBySchool(schoolId);
    }

    @GetMapping("/expiring-soon")
    public List<SubscriptionDto> getExpiringSoon(@RequestParam(defaultValue = "30") int daysAhead) {
        return subscriptionService.findExpiringSoon(daysAhead);
    }

    @GetMapping("/{id}")
    public SubscriptionDto getById(@PathVariable Long id) {
        return subscriptionService.findById(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public SubscriptionDto create(@RequestBody SubscriptionDto dto) {
        return subscriptionService.create(dto);
    }

    @PatchMapping("/{id}/status")
    public SubscriptionDto updateStatus(@PathVariable Long id, @RequestParam SubscriptionStatus status) {
        return subscriptionService.updateStatus(id, status);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id) {
        subscriptionService.delete(id);
    }
}
