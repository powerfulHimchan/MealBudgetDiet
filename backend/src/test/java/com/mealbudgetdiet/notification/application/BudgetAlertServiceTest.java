package com.mealbudgetdiet.notification.application;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class BudgetAlertServiceTest {

	@Test
	void requiresBothEightyPercentUsageAndMoreThanDoubleDailyAllowance() {
		assertThat(BudgetAlertService.matchesAlertCondition(100_000, 79_000, 21_000, 2, 30)).isFalse();
		assertThat(BudgetAlertService.matchesAlertCondition(100_000, 80_000, 20_000, 3, 30)).isFalse();
		assertThat(BudgetAlertService.matchesAlertCondition(100_000, 85_000, 15_000, 2, 30)).isTrue();
		assertThat(BudgetAlertService.matchesAlertCondition(100_000, 110_000, -10_000, 1, 30)).isFalse();
	}

	@Test
	void comparesLargeAmountsWithoutLongOverflow() {
		assertThat(BudgetAlertService.matchesAlertCondition(
			Long.MAX_VALUE, Long.MAX_VALUE - 1, 1, 1, 31)).isFalse();
	}
}
