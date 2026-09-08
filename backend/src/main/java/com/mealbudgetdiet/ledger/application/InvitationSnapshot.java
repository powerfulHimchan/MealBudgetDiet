package com.mealbudgetdiet.ledger.application;

import java.time.Instant;
import java.util.UUID;

public record InvitationSnapshot(
	UUID id,
	String maskedCode,
	InvitationStatus status,
	long useCount,
	Instant createdAt,
	Instant lastUsedAt
) {
}
