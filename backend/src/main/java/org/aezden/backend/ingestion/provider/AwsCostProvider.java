package org.aezden.backend.ingestion.provider;

import lombok.RequiredArgsConstructor;
import org.aezden.backend.cost.Provider;
import org.aezden.backend.ingestion.mapper.RecordToRaw;
import software.amazon.awssdk.services.costexplorer.CostExplorerClient;
import software.amazon.awssdk.services.costexplorer.model.DateInterval;
import software.amazon.awssdk.services.costexplorer.model.GetCostAndUsageRequest;
import software.amazon.awssdk.services.costexplorer.model.GetCostAndUsageResponse;
import software.amazon.awssdk.services.costexplorer.model.Granularity;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Component;


@RequiredArgsConstructor
@Component
public class AwsCostProvider implements CloudCostProvider {

    private final CostExplorerClient costExplorerClient;
    private final RecordToRaw recordToRaw;

    @Override
    public Provider provider() {
        return Provider.AWS;
    }

    @Override
    public List<RawCostRecord> fetchCosts(String accountId, LocalDate startDate, LocalDate endDate) {
        List<RawCostRecord> records = new ArrayList<>();
        String nextPageToken = null;
        do {
            GetCostAndUsageRequest.Builder request = GetCostAndUsageRequest.builder()
                    .timePeriod(DateInterval.builder()
                            .start(startDate.toString())
                            .end(endDate.toString())
                            .build())
                    .granularity(Granularity.DAILY)
                    .metrics("UnblendedCost")
                    .groupBy(
                            software.amazon.awssdk.services.costexplorer.model.GroupDefinition.builder()
                                    .type("DIMENSION").key("SERVICE").build(),
                            software.amazon.awssdk.services.costexplorer.model.GroupDefinition.builder()
                                    .type("DIMENSION").key("REGION").build());
            if (nextPageToken != null) {
                request.nextPageToken(nextPageToken);
            }
            GetCostAndUsageResponse response = costExplorerClient.getCostAndUsage(request.build());
            records.addAll(recordToRaw.recordToRaw(response));
            nextPageToken = response.nextPageToken();
        } while (nextPageToken != null && !nextPageToken.isBlank());
        return records;
    }
}
