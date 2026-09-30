package org.aezden.backend.cost.dto;

import java.time.LocalDate;
import org.aezden.backend.cost.Provider;
import java.util.UUID;

public record CostQuery(
        Provider provider,
        UUID accountId,
        LocalDate startDate,
        LocalDate endDate
) {
}
