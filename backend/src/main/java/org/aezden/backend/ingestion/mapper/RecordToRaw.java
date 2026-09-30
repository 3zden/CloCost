package org.aezden.backend.ingestion.mapper;

import org.aezden.backend.ingestion.provider.RawCostRecord;
import software.amazon.awssdk.services.costexplorer.model.Group;
import software.amazon.awssdk.services.costexplorer.model.MetricValue;
import software.amazon.awssdk.services.costexplorer.model.ResultByTime;
import software.amazon.awssdk.services.costexplorer.model.GetCostAndUsageResponse;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

@Component
public class RecordToRaw {

    private static final String COST_METRIC = "UnblendedCost";
    private static final String TOTAL_SERVICE = "TOTAL";

    public List<RawCostRecord> recordToRaw(GetCostAndUsageResponse response) {
        Objects.requireNonNull(response, "Cost Explorer response must not be null");

        List<RawCostRecord> records = new ArrayList<>();
        for (ResultByTime result : response.resultsByTime()) {
            LocalDate usageDate = LocalDate.parse(result.timePeriod().start());

            if (!result.groups().isEmpty()) {
                for (Group group : result.groups()) {
                    MetricValue metric = group.metrics().get(COST_METRIC);
                    if (metric != null) {
                        records.add(toRawRecord(
                                group.keys().isEmpty() ? TOTAL_SERVICE : group.keys().get(0),
                                group.keys().size() > 1 ? group.keys().get(1) : null,
                                usageDate,
                                metric
                        ));
                    }
                }
            } else {
                MetricValue metric = result.total().get(COST_METRIC);
                if (metric != null) {
                    records.add(toRawRecord(TOTAL_SERVICE, null, usageDate, metric));
                }
            }
        }
        return records;
    }

    private RawCostRecord toRawRecord(
            String service,
            String region,
            LocalDate usageDate,
            MetricValue metric
    ) {
        return new RawCostRecord(
                service,
                region,
                usageDate,
                new BigDecimal(metric.amount()),
                metric.unit()
        );
    }
}
