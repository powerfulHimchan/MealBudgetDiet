package com.mealbudgetdiet.ledger.application;

import com.mealbudgetdiet.budget.domain.BudgetCycleUnit;

public record LedgerSettingsSnapshot(BudgetCycleUnit budgetCycleUnit, int budgetCycleStartDay,
	int budgetWeekStartDay, Long defaultWeeklyBudget, int version) {
}
