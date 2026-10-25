package com.mealbudgetdiet.analytics.application;

import java.math.BigDecimal;
import java.time.LocalDate;
import com.mealbudgetdiet.budget.domain.BudgetCycleUnit;

public record WidgetSummary(BudgetCycleUnit cycleUnit, DashboardSnapshot.Period period,
    long budget, long spent, long remaining, BigDecimal usageRate, DashboardStatus status,
    long todaySpent, LocalDate today) {
}
