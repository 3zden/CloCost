package org.aezden.backend.cost.dto;

import java.time.LocalDate;

public record CostQuery(
        String provider,
        String accountId,
        LocalDate startDate,
        LocalDate endDate
) {
}
