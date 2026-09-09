package com.mealbudgetdiet.identity.api;

import java.util.UUID;

import com.mealbudgetdiet.identity.infrastructure.MealBudgetPrincipal;
import com.mealbudgetdiet.ledger.domain.MemberRole;

public record AuthUserResponse(UUID id, String email, String displayName, MemberRole role, String profileImageUrl) {

	static AuthUserResponse from(MealBudgetPrincipal principal) {
		return from(principal, null);
	}

	static AuthUserResponse from(MealBudgetPrincipal principal, UUID profileImageId) {
		return new AuthUserResponse(
			principal.id(), principal.email(), principal.displayName(), principal.role(),
			profileImageId == null ? null : "/api/v1/images/" + profileImageId + "/content"
		);
	}
}
