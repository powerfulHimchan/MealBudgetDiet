package com.mealbudgetdiet.ledger.application;

import com.mealbudgetdiet.budget.domain.BudgetCycleUnit;
import com.mealbudgetdiet.ledger.domain.Ledger;

public record BudgetCycleSettings(BudgetCycleUnit budgetCycleUnit, int budgetCycleStartDay, int budgetWeekStartDay) {
	public static BudgetCycleSettings from(Ledger ledger) {
		return new BudgetCycleSettings(ledger.getBudgetCycleUnit(), ledger.getBudgetCycleStartDay(), ledger.getBudgetWeekStartDay());
	}
}
