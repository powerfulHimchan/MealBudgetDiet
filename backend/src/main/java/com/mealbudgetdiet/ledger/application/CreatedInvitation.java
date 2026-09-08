package com.mealbudgetdiet.ledger.application;

import java.time.Instant;
import java.util.UUID;

public record CreatedInvitation(UUID id, String code, String joinUrl, Instant createdAt) {
}
