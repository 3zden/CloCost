package org.aezden.backend.cost;


import org.aezden.backend.cost.dto.CostQuery;
import org.aezden.backend.cost.dto.CostResponse;
import org.aezden.backend.cost.dto.CostSummaryResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.List;

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
        );
        return ResponseEntity.ok(records);
    }

    public ResponseEntity<CostSummaryResponse> getSummary() {
        List<CostResponse> result = costRepo.getAllByDateBefore(LocalDate.now());
        return null;
    }
}
