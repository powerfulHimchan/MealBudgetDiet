package com.mealbudgetdiet.analytics.application;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record StatisticsSnapshot(
	Period period,
	long totalAmount,
	Budget budget,
	Comparison comparison,
	List<DailyAmount> daily,
	List<CategoryAmount> categories
) {
	public record Period(LocalDate from, LocalDate to) {
	}

	public record Budget(long amount, BigDecimal usageRate) {
	}

	public record Comparison(
		LocalDate from,
		LocalDate to,
		long totalAmount,
		long changeAmount,
		BigDecimal changeRate
	) {
	}

	public record DailyAmount(LocalDate date, long amount) {
	}

	public record CategoryAmount(
		UUID categoryId,
		String categoryName,
		long amount,
		BigDecimal ratio
	) {
	}
}
