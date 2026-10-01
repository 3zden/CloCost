package org.aezden.backend.anomaly;

import lombok.RequiredArgsConstructor;
import org.aezden.backend.cost.*;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.*;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class AnomalyDetectionService {
    private final CostRepo costRepository;
    private final AnomalyRepository anomalyRepository;
    private final ApplicationEventPublisher events;

    @Scheduled(cron = "${app.anomaly.cron:0 0 6 * * *}")
    public void detect() {
        LocalDate today = LocalDate.now();

        List<CostRecord> history = costRepository.findCosts(null, null, today.minusDays(30), today.minusDays(1));

        Map<List<Object>, List<BigDecimal>> grouped = history.stream().collect(Collectors.groupingBy(
                record -> List.of(record.getProvider(), record.getService()),
                Collectors.mapping(CostRecord::getCost, Collectors.toList())));

        for (Map.Entry<List<Object>, List<BigDecimal>> entry : grouped.entrySet()) {
            List<BigDecimal> values = entry.getValue();
            if (values.size() < 3) continue;
            double mean = values.stream().mapToDouble(BigDecimal::doubleValue).average().orElse(0);
            double variance = values.stream().mapToDouble(v -> Math.pow(v.doubleValue() - mean, 2)).average().orElse(0);
            double stddev = Math.sqrt(variance);
            BigDecimal actual = costRepository.findCosts((Provider) entry.getKey().get(0), null, today, today)
                    .stream().map(CostRecord::getCost).reduce(BigDecimal.ZERO, BigDecimal::add);
            if (stddev > 0 && actual.doubleValue() > mean + (3 * stddev)) {
                BigDecimal expected = BigDecimal.valueOf(mean).setScale(4, RoundingMode.HALF_UP);
                BigDecimal deviation = actual.subtract(expected).multiply(BigDecimal.valueOf(100))
                        .divide(expected, 2, RoundingMode.HALF_UP);
                Anomaly anomaly = anomalyRepository.save(Anomaly.builder()
                        .provider((Provider) entry.getKey().get(0)).service((String) entry.getKey().get(1))
                        .detectedAt(Instant.now()).expectedCost(expected).actualCost(actual)
                        .deviationPercent(deviation).status(AnomalyStatus.OPEN).build());
                events.publishEvent(new AnomalyDetectedEvent(anomaly));
            }
        }
    }
}
