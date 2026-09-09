package com.mealbudgetdiet.budget.application;

import java.time.YearMonth;

import com.mealbudgetdiet.budget.domain.BudgetCycle;

public record BudgetSnapshot(
	YearMonth yearMonth,
	BudgetCycle period,
	long amount,
	BudgetSource source,
	int version
) {
}
