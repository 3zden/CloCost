package org.aezden.backend.cost.dto;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;

public record CostSummaryResponse(
        BigDecimal totalCost,
        List<String> byProvider,

        String currency,
        HashMap<String, BigDecimal> byService
) {
}
