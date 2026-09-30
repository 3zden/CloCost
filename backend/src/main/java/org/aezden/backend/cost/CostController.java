package org.aezden.backend.cost;

import org.aezden.backend.cost.dto.CostQuery;
import org.aezden.backend.cost.dto.CostResponse;
import org.aezden.backend.cost.dto.CostSummaryResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/v1/costs")
public class CostController {

    private final CostService costService;

    public CostController(CostService costService){
        this.costService = costService;
    }

    @GetMapping
    public ResponseEntity<List<CostResponse>> getCosts(
            @RequestParam(required = false) Provider provider,
            @RequestParam(required = false) java.util.UUID accountId,
            @RequestParam(required = false) LocalDate startDate,
            @RequestParam(required = false) LocalDate endDate

            ){
        CostQuery costQuery = new CostQuery(provider, accountId, startDate, endDate);
        return costService.getCosts(costQuery);
    }

    @GetMapping("/summary")
    public ResponseEntity<CostSummaryResponse> getSummary(){
        return costService.getSummary();
    }
}
