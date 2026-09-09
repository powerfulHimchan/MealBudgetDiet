package com.mealbudgetdiet.notification.application;

import java.math.BigDecimal;
import java.math.RoundingMode;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.mealbudgetdiet.notification.domain.BudgetAlertType;
import com.mealbudgetdiet.notification.infrastructure.PushOutboxStore;
import com.mealbudgetdiet.notification.infrastructure.WebPushGateway;

@Component
public class PushDispatcher {

	private final PushOutboxStore outboxStore;
	private final WebPushGateway webPushGateway;
	private final boolean enabled;

	public PushDispatcher(
		PushOutboxStore outboxStore,
		WebPushGateway webPushGateway,
		@Value("${app.push.dispatch-enabled:true}") boolean enabled
	) {
		this.outboxStore = outboxStore;
		this.webPushGateway = webPushGateway;
		this.enabled = enabled;
	}

	@Scheduled(
		initialDelayString = "${app.push.initial-delay-ms:10000}",
		fixedDelayString = "${app.push.dispatch-interval-ms:30000}"
	)
	public void dispatch() {
		if (!enabled || !webPushGateway.isConfigured()) {
			return;
		}
		for (PushDeliveryTask task : outboxStore.claim(20)) {
			var result = webPushGateway.send(task, payload(task));
			if (result.success()) {
				outboxStore.markSent(task.deliveryId());
			} else {
				outboxStore.markFailed(task, result.expired(), result.retryable(), result.error());
			}
		}
	}

	static String payload(PushDeliveryTask task) {
		if (task.alertType() == BudgetAlertType.MONTHLY_BUDGET_SURPLUS) {
			return surplusPayload(task);
		}
		int elapsedDays = task.cycleDays() - task.remainingDays() + 1;
		long projectedSpend = BigDecimal.valueOf(task.totalSpent())
			.multiply(BigDecimal.valueOf(task.cycleDays()))
			.divide(BigDecimal.valueOf(elapsedDays), 0, RoundingMode.HALF_UP)
			.longValue();
		String title = task.totalSpent() > task.monthlyBudget()
			? "이번 예산 주기 식비 예산을 초과했어요"
			: "이번 예산 주기 식비 예산 초과가 예상돼요";
		String body = task.totalSpent() > task.monthlyBudget()
			? "현재 식비가 예산보다 %,d원 많아요.".formatted(task.totalSpent() - task.monthlyBudget())
			: "현재 소비 속도라면 이번 예산 주기에 약 %,d원을 사용할 것으로 예상돼요.".formatted(projectedSpend);
		return """
			{"notificationId":"%s","type":"%s","title":"%s","body":"%s","data":{"url":"/","yearMonth":"%s"}}
			""".formatted(task.alertId(), task.alertType().name(), title, body,
			task.alertMonth().toString().substring(0, 7)).strip();
	}

	private static String surplusPayload(PushDeliveryTask task) {
		long remaining = task.monthlyBudget() - task.totalSpent();
		long dailyAllowance = BigDecimal.valueOf(remaining)
			.divide(BigDecimal.valueOf(task.remainingDays()), 0, RoundingMode.HALF_UP)
			.longValue();
		return """
			{"notificationId":"%s","type":"MONTHLY_BUDGET_SURPLUS","title":"이번 달 식비 예산에 여유가 있어요","body":"남은 기간 동안 하루 평균 %,d원을 사용할 수 있어요.","data":{"url":"/","yearMonth":"%s"}}
			""".formatted(task.alertId(), dailyAllowance, task.alertMonth().toString().substring(0, 7)).strip();
	}
}
