package com.mealbudgetdiet.expense.application;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record ExpenseSnapshot(
	UUID id,
	long amount,
	LocalDate spentOn,
	CategorySnapshot category,
	String merchant,
	String memo,
	int version,
	Instant createdAt,
	Instant updatedAt
) {
}
