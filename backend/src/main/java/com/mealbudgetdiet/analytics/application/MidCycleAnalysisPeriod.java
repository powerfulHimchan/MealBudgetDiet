package com.mealbudgetdiet.analytics.application;

import java.time.LocalDate;
import com.mealbudgetdiet.budget.domain.BudgetCycle;

public final class MidCycleAnalysisPeriod {
	private MidCycleAnalysisPeriod() {
	}

	public static LocalDate midpoint(BudgetCycle cycle) {
		return cycle.from().plusDays((cycle.days() - 1) / 2);
	}

	public static boolean eligible(BudgetCycle cycle, LocalDate today) {
		return cycle.contains(today) && !today.isBefore(midpoint(cycle));
	}
}
