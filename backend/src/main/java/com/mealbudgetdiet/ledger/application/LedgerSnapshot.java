package com.mealbudgetdiet.ledger.application;

import java.util.UUID;

import com.mealbudgetdiet.ledger.domain.MemberRole;

public record LedgerSnapshot(
	UUID id,
	String name,
	long defaultMonthlyBudget,
	int budgetCycleStartDay,
	int pushUsageThreshold,
	long memberCount,
	MemberRole currentUserRole,
	int version
) {
}
