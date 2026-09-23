package org.aezden.backend.cost;


import org.aezden.backend.cost.dto.CostQuery;
import org.aezden.backend.cost.dto.CostResponse;
import org.aezden.backend.cost.dto.CostSummaryResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

@Service
public class CostService {

    private CostRepo costRepo;
    public CostService(CostRepo costRepo){
        this.costRepo = costRepo;
    }

    public ResponseEntity<List<CostResponse>> getCosts(CostQuery costQuery) {
        List<CostResponse> records = costRepo.findCosts(
                costQuery.provider(),
                costQuery.accountId(),
                costQuery.startDate(),
                costQuery.endDate()
        ).stream().map(this::toResponse).toList();
        return ResponseEntity.ok(records);
    }

    public ResponseEntity<CostSummaryResponse> getSummary() {
        List<CostRecord> records = costRepo.getAllByUsageDateBefore(LocalDate.now());
        BigDecimal totalCost = records.stream()
                .map(CostRecord::getCost)
                .filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        List<String> providers = records.stream()
                .map(CostRecord::getProvider)
                .filter(Objects::nonNull)
                .map(Enum::name)
                .distinct()
                .sorted()
                .toList();
        HashMap<String, BigDecimal> byService = records.stream()
                .filter(record -> record.getService() != null)
                .collect(Collectors.groupingBy(
                        CostRecord::getService,
                        HashMap::new,
                        Collectors.reducing(
                                BigDecimal.ZERO,
                                CostRecord::getCost,
                                BigDecimal::add
                        )
                ));
        String currency = records.stream()
                .map(CostRecord::getCurrency)
                .filter(Objects::nonNull)
                .findFirst()
                .orElse("USD");

        return ResponseEntity.ok(new CostSummaryResponse(
                totalCost,
                providers,
                currency,
                byService
        ));
    }

    private CostResponse toResponse(CostRecord record) {
        return new CostResponse(
                record.getUsageDate(),
                record.getProvider() == null ? null : record.getProvider().name(),
                record.getCost(),
                record.getCurrency()
        );
    }
}
