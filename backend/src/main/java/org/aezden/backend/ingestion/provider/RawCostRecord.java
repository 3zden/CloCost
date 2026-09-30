package org.aezden.backend.ingestion.provider;

import java.math.BigDecimal;
import java.time.LocalDate;

public record RawCostRecord(
        String service,
        String region,
        LocalDate usageDate,
        BigDecimal cost,
        String currency

) {
}
