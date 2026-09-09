package com.mealbudgetdiet.ledger.application;

import java.util.UUID;

import com.mealbudgetdiet.ledger.domain.MemberRole;

public record LedgerSnapshot(
	UUID id,
	String name,
	long defaultMonthlyBudget,
	int budgetCycleStartDay,
	long memberCount,
	MemberRole currentUserRole,
	int version
) {
}
