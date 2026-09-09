package com.mealbudgetdiet.notification.application;

import java.time.Instant;
import java.util.UUID;

import com.mealbudgetdiet.notification.domain.PushSubscriptionStatus;

public record PushSubscriptionSnapshot(
	UUID id,
	PushSubscriptionStatus status,
	Instant createdAt,
	boolean created
) {
}
