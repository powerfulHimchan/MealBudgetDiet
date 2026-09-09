package com.mealbudgetdiet.budget.domain;

import java.time.LocalDate;
import java.time.YearMonth;
import java.time.temporal.ChronoUnit;

public record BudgetCycle(YearMonth yearMonth, LocalDate from, LocalDate to) {

	public static BudgetCycle containing(LocalDate date, int startDay) {
		validateStartDay(startDay);
		YearMonth candidate = YearMonth.from(date);
		if (date.isBefore(startDate(candidate, startDay))) {
			candidate = candidate.minusMonths(1);
		}
		return starting(candidate, startDay);
	}

	public static BudgetCycle starting(YearMonth yearMonth, int startDay) {
		validateStartDay(startDay);
		LocalDate from = startDate(yearMonth, startDay);
		LocalDate to = startDate(yearMonth.plusMonths(1), startDay).minusDays(1);
		return new BudgetCycle(yearMonth, from, to);
	}

	public int days() {
		return Math.toIntExact(ChronoUnit.DAYS.between(from, to) + 1);
	}

	public boolean contains(LocalDate date) {
		return !date.isBefore(from) && !date.isAfter(to);
	}

	private static LocalDate startDate(YearMonth yearMonth, int startDay) {
		return yearMonth.atDay(Math.min(startDay, yearMonth.lengthOfMonth()));
	}

	private static void validateStartDay(int startDay) {
		if (startDay < 1 || startDay > 31) {
			throw new IllegalArgumentException("예산 주기 시작일은 1~31이어야 합니다.");
		}
	}
}
