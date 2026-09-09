package com.mealbudgetdiet.notification.application;

import java.time.LocalDate;
import java.util.UUID;

import com.mealbudgetdiet.notification.domain.BudgetAlertType;

public record PushDeliveryTask(
	UUID deliveryId,
	int attemptCount,
	UUID alertId,
	BudgetAlertType alertType,
	LocalDate alertMonth,
	long monthlyBudget,
	long totalSpent,
	int remainingDays,
	int cycleDays,
	UUID subscriptionId,
	String endpoint,
	String p256dhKey,
	String authKey
) {
}
