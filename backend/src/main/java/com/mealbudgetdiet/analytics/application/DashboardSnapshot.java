package com.mealbudgetdiet.analytics.application;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.UUID;
import com.mealbudgetdiet.budget.domain.BudgetCycleUnit;

public record DashboardSnapshot(
	YearMonth yearMonth,
	BudgetCycleUnit cycleUnit,
	Period period,
	long budget,
	long spent,
	long remaining,
	long projectedSpent,
	BigDecimal usageRate,
	int pushUsageThreshold,
	DashboardStatus status,
	List<RecentExpense> recentExpenses
) {
	public record Period(LocalDate from, LocalDate to) {
	}

	public record RecentExpense(
		UUID id,
		long amount,
		LocalDate spentOn,
		String categoryName,
		String merchant,
		int version
	) {
	}
}
