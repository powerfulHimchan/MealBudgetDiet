package com.mealbudgetdiet.ledger.application;

import java.util.UUID;

import com.mealbudgetdiet.ledger.domain.MemberRole;
import com.mealbudgetdiet.budget.domain.BudgetCycleUnit;

public record LedgerSnapshot(
	UUID id,
	String name,
	long defaultMonthlyBudget,
	int budgetCycleStartDay,
	BudgetCycleUnit budgetCycleUnit,
	int budgetWeekStartDay,
	Long defaultWeeklyBudget,
	int pushUsageThreshold,
	long memberCount,
	MemberRole currentUserRole,
	int version
) {
}
