package com.mealbudgetdiet.notification.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.mealbudgetdiet.notification.domain.BudgetAlertType;

class PushDispatcherTest {

	@Test
	void describesProjectedMonthlySpendForOverrunRisk() {
		String payload = PushDispatcher.payload(task(BudgetAlertType.MONTHLY_BUDGET_OVERRUN_RISK, 100_000, 85_000, 16));

		assertThat(payload)
			.contains("\"type\":\"MONTHLY_BUDGET_OVERRUN_RISK\"")
			.contains("이번 달 식비 예산 초과가 예상돼요")
			.contains("이번 달 약 170,000원을 사용할 것으로 예상돼요");
	}

	@Test
	void describesAmountAlreadyOverBudget() {
		String payload = PushDispatcher.payload(task(BudgetAlertType.MONTHLY_BUDGET_OVERRUN_RISK, 100_000, 110_000, 1));

		assertThat(payload)
			.contains("이번 달 식비 예산을 초과했어요")
			.contains("예산보다 10,000원 많아요");
	}

	@Test
	void keepsPayloadMeaningForPendingLegacySurplusDelivery() {
		String payload = PushDispatcher.payload(task(BudgetAlertType.MONTHLY_BUDGET_SURPLUS, 100_000, 80_000, 2));

		assertThat(payload)
			.contains("\"type\":\"MONTHLY_BUDGET_SURPLUS\"")
			.contains("하루 평균 10,000원을 사용할 수 있어요");
	}

	private PushDeliveryTask task(BudgetAlertType alertType, long budget, long spent, int remainingDays) {
		return new PushDeliveryTask(
			UUID.randomUUID(), 1, UUID.randomUUID(), alertType, LocalDate.of(2026, 9, 1),
			budget, spent, remainingDays, UUID.randomUUID(), "https://example.com", "key", "auth");
	}
}
