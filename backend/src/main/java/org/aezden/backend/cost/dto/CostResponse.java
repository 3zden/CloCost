package org.aezden.backend.cost.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

public record CostResponse(
        LocalDate date,
        String provider,
        BigDecimal total,
        String currency
) {
}
