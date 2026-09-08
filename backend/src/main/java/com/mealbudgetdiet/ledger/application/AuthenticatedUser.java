package com.mealbudgetdiet.ledger.application;

import java.util.UUID;

import com.mealbudgetdiet.ledger.domain.MemberRole;

public record AuthenticatedUser(UUID id, String email, String displayName, MemberRole role) {
}
