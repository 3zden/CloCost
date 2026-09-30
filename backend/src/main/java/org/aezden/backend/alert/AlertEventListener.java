package org.aezden.backend.alert;

import lombok.RequiredArgsConstructor;
import org.aezden.backend.anomaly.AnomalyDetectedEvent;
import org.aezden.backend.budget.BudgetThresholdExceededEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import java.time.Instant;

@Component
@RequiredArgsConstructor
public class AlertEventListener {
    private final AlertRepository repository;

    @EventListener
    public void onBudgetExceeded(BudgetThresholdExceededEvent event) {
        repository.save(Alert.builder().budgetId(event.budget().getId())
                .message("Budget exceeded 85%: " + event.budget().getName())
                .severity(AlertSeverity.WARNING).triggeredAt(Instant.now()).build());
    }

    @EventListener
    public void onAnomalyDetected(AnomalyDetectedEvent event) {
        repository.save(Alert.builder().anomalyId(event.anomaly().getId())
                .message("Cost anomaly detected for " + event.anomaly().getService())
                .severity(AlertSeverity.CRITICAL).triggeredAt(Instant.now()).build());
    }
}
