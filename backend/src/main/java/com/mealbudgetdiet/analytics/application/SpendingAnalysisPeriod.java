package com.mealbudgetdiet.analytics.application;

import java.time.LocalDate;
import com.mealbudgetdiet.budget.domain.BudgetCycle;
import com.mealbudgetdiet.ledger.domain.Ledger;

final class SpendingAnalysisPeriod {
	private SpendingAnalysisPeriod() {
	}

	static boolean eligible(Ledger ledger, LocalDate from, LocalDate to, LocalDate today) {
		if (from == null || to == null || from.isAfter(to) || !to.isBefore(today)) return false;
		var cycle = BudgetCycle.containing(from, ledger.getBudgetCycleUnit(),
			ledger.getBudgetCycleStartDay(), ledger.getBudgetWeekStartDay());
		return cycle.from().equals(from) && cycle.to().equals(to);
	}
}
