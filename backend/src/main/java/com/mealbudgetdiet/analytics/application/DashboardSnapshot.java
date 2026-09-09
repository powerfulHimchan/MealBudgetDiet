package com.mealbudgetdiet.analytics.application;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.UUID;

public record DashboardSnapshot(
	YearMonth yearMonth,
	Period period,
	long budget,
	long spent,
	long remaining,
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
