package org.aezden.backend.cost;

import org.aezden.backend.cost.dto.CostQuery;
import org.aezden.backend.cost.dto.CostResponse;
import org.aezden.backend.cost.dto.CostSummaryResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.Mapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.time.LocalDate;
import java.util.List;

@Controller
@RequestMapping("/api/v1/costs")
public class CostController {

    private final CostService costService;

    public CostController(CostService costService){
        this.costService = costService;
    }

    @GetMapping("/")
    public ResponseEntity<List<CostResponse>> getCosts(
            @RequestParam(required = false) String provider,
            @RequestParam(required = false) String accountId,
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
