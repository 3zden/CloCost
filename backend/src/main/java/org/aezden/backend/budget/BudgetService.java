package org.aezden.backend.budget;

import lombok.RequiredArgsConstructor;
import org.aezden.backend.cost.CostRepo;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import java.math.BigDecimal;
import java.time.*;
import java.util.List;

@Service
@RequiredArgsConstructor
public class BudgetService {
    private final BudgetRepository budgetRepository;
    private final CostRepo costRepository;
    private final ApplicationEventPublisher events;

    public List<BudgetStatus> list() {
        LocalDate today = LocalDate.now();
        return budgetRepository.findAll().stream().map(budget -> {
            LocalDate start = budget.getPeriod() == BudgetPeriod.MONTHLY
                    ? today.withDayOfMonth(1) : today.minusDays(today.getDayOfWeek().getValue() - 1L);
            BigDecimal spend = costRepository.findCosts(null, null, start, today).stream()
                    .map(record -> record.getCost() == null ? BigDecimal.ZERO : record.getCost())
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            BigDecimal ratio = budget.getLimitAmount().signum() == 0 ? BigDecimal.ONE
                    : spend.divide(budget.getLimitAmount(), 4, java.math.RoundingMode.HALF_UP);
            if (ratio.compareTo(BigDecimal.valueOf(.85)) >= 0) {
                events.publishEvent(new BudgetThresholdExceededEvent(budget, spend));
            }
            return new BudgetStatus(budget, spend, ratio);
        }).toList();
    }

    public Budget create(Budget budget) {
        budget.setCreatedAt(Instant.now());
        if (budget.getCurrency() == null) budget.setCurrency("USD");
        return budgetRepository.save(budget);
    }

    public record BudgetStatus(Budget budget, BigDecimal currentSpend, BigDecimal utilization) {}
}
