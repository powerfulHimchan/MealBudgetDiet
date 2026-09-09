package com.mealbudgetdiet.budget.application;

import java.time.YearMonth;

public record BudgetSnapshot(
	YearMonth yearMonth,
	long amount,
	BudgetSource source,
	int version
) {
}
