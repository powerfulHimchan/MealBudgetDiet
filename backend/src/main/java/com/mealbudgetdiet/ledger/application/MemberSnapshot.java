package com.mealbudgetdiet.ledger.application;

import java.time.Instant;
import java.util.UUID;

import com.mealbudgetdiet.ledger.domain.MemberRole;

public record MemberSnapshot(UUID id, String displayName, MemberRole role, Instant joinedAt, String profileImageUrl) {
}
