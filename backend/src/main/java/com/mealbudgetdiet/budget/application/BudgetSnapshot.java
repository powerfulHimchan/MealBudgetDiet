package com.mealbudgetdiet.budget.application;

import java.time.YearMonth;

import com.mealbudgetdiet.budget.domain.BudgetCycle;

public record BudgetSnapshot(
	YearMonth yearMonth,
	BudgetCycle period,
	long amount,
	BudgetSource source,
	int version,
	BudgetRolloverSnapshot rollover
) {
	public BudgetSnapshot(YearMonth yearMonth, BudgetCycle period, long amount, BudgetSource source, int version) {
		this(yearMonth, period, amount, source, version, BudgetRolloverSnapshot.disabled(amount));
	}
	public long appliedAmount() { return rollover.effectiveAmount(); }
}
