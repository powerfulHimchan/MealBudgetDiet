package com.mealbudgetdiet.notification.application;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class BudgetAlertServiceTest {
	@Test
	void alertsWhenRolloverLeavesNoAvailableBudget() {
		assertThat(BudgetAlertService.matchesAlertCondition(-100000, 0, 80, 1, 30)).isTrue();
		assertThat(BudgetAlertService.matchesAlertCondition(0, 1000, 80, 1, 30)).isTrue();
		assertThat(BudgetAlertService.matchesAlertCondition(0, 0, 80, 1, 30)).isFalse();
	}

	@Test
	void requiresBothConfiguredUsageThresholdAndProjectedOverrun() {
		assertThat(BudgetAlertService.matchesAlertCondition(100_000, 89_000, 90, 10, 30)).isFalse();
		assertThat(BudgetAlertService.matchesAlertCondition(100_000, 90_000, 90, 10, 30)).isTrue();
		assertThat(BudgetAlertService.matchesAlertCondition(100_000, 90_000, 90, 27, 30)).isFalse();
		assertThat(BudgetAlertService.matchesAlertCondition(100_000, 110_000, 90, 30, 30)).isTrue();
		assertThat(BudgetAlertService.matchesAlertCondition(100_000, 100_000, 90, 30, 30)).isFalse();
		assertThat(BudgetAlertService.matchesAlertCondition(100_000, 50_000, 50, 10, 30)).isTrue();
		assertThat(BudgetAlertService.matchesAlertCondition(100_000, 50_000, 0, 10, 30)).isFalse();
		assertThat(BudgetAlertService.matchesAlertCondition(100_000, 50_000, 101, 10, 30)).isFalse();
	}

	@Test
	void comparesLargeAmountsWithoutLongOverflow() {
		assertThat(BudgetAlertService.matchesAlertCondition(
			Long.MAX_VALUE, Long.MAX_VALUE - 1, 80, 1, 31)).isTrue();
	}
}
