package com.mealbudgetdiet.budget.application;

public record BudgetRolloverSnapshot(boolean enabled, long baseAmount, long carryAmount, long effectiveAmount) {

	public static BudgetRolloverSnapshot disabled(long base) {
		return new BudgetRolloverSnapshot(false, base, 0, base);
	}
}
