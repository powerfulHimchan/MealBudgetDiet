package com.mealbudgetdiet.notification.application;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class BudgetAlertServiceTest {

	@Test
	void requiresBothEightyPercentUsageAndProjectedOverrun() {
		assertThat(BudgetAlertService.matchesAlertCondition(100_000, 79_000, 10, 30)).isFalse();
		assertThat(BudgetAlertService.matchesAlertCondition(100_000, 80_000, 10, 30)).isTrue();
		assertThat(BudgetAlertService.matchesAlertCondition(100_000, 80_000, 24, 30)).isFalse();
		assertThat(BudgetAlertService.matchesAlertCondition(100_000, 80_000, 25, 30)).isFalse();
		assertThat(BudgetAlertService.matchesAlertCondition(100_000, 110_000, 30, 30)).isTrue();
		assertThat(BudgetAlertService.matchesAlertCondition(100_000, 100_000, 30, 30)).isFalse();
	}

	@Test
	void comparesLargeAmountsWithoutLongOverflow() {
		assertThat(BudgetAlertService.matchesAlertCondition(
			Long.MAX_VALUE, Long.MAX_VALUE - 1, 1, 31)).isTrue();
	}
}
