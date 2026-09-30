package org.aezden.backend.ingestion.provider;

import org.aezden.backend.cost.Provider;

import java.time.LocalDate;
import java.util.List;

public interface CloudCostProvider {

    Provider provider();

    List<RawCostRecord> fetchCosts(
            String accountId,
            LocalDate startDate,
            LocalDate endDate
    );
}
