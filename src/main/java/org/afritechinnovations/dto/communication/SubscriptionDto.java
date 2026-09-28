package org.afritechinnovations.dto.communication;

import lombok.*;
import org.afritechinnovations.model.communication.SubscriptionStatus;

import java.time.LocalDate;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SubscriptionDto {
    private Long id;
    private Long schoolId;
    private String plan;
    private LocalDate startDate;
    private LocalDate endDate;
    private SubscriptionStatus status;
}
