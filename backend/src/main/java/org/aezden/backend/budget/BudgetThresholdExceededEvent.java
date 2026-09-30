package org.aezden.backend.budget;

import java.math.BigDecimal;

public record BudgetThresholdExceededEvent(Budget budget, BigDecimal spend) {}
