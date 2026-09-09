package com.mealbudgetdiet.notification.application;

import java.time.LocalDate;
import java.util.UUID;

public record PushDeliveryTask(
	UUID deliveryId,
	int attemptCount,
	UUID alertId,
	LocalDate alertMonth,
	long monthlyBudget,
	long totalSpent,
	int remainingDays,
	UUID subscriptionId,
	String endpoint,
	String p256dhKey,
	String authKey
) {
}
