package com.mealbudgetdiet.expense.application;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import java.util.List;
import com.mealbudgetdiet.media.application.ExpenseImageSnapshot;

public record ExpenseSnapshot(
	UUID id,
	long amount,
	LocalDate spentOn,
	CategorySnapshot category,
	String merchant,
	String memo,
	List<ExpenseImageSnapshot> images,
	int version,
	Instant createdAt,
	Instant updatedAt
) {
}
