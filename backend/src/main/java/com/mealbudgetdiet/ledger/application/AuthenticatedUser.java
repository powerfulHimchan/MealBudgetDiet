package com.mealbudgetdiet.ledger.application;

import java.util.UUID;

import com.mealbudgetdiet.identity.domain.ServiceRole;
import com.mealbudgetdiet.ledger.domain.MemberRole;

public record AuthenticatedUser(
	UUID id,
	String email,
	String displayName,
	ServiceRole serviceRole,
	MemberRole ledgerRole
) {
}
