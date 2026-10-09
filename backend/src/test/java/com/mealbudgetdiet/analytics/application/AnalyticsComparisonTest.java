package com.mealbudgetdiet.analytics.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import com.mealbudgetdiet.budget.application.BudgetService;
import com.mealbudgetdiet.budget.domain.BudgetCycleUnit;
import com.mealbudgetdiet.expense.infrastructure.CategoryRepository;
import com.mealbudgetdiet.ledger.application.LedgerAccessService;
import com.mealbudgetdiet.ledger.domain.Ledger;
import com.mealbudgetdiet.ledger.domain.LedgerMember;
import com.mealbudgetdiet.ledger.domain.MemberRole;

class AnalyticsComparisonTest {

	@Test
	void comparesOctoberNineDaysAgainstSeptemberNineDaysAndExcludesLaterExpenses() {
		var result = statistics(new Ledger("장부", 500000), "2026-10-01", "2026-10-31",
			"2026-10-08T15:30:00Z", Map.of(
				date("2026-10-09"), 112000L, date("2026-10-20"), 99000L,
				date("2026-09-09"), 100000L, date("2026-09-20"), 80000L));
		assertThat(result.totalAmount()).isEqualTo(211000);
		var comparison = result.comparison();
		assertThat(comparison.from()).isEqualTo(date("2026-09-01"));
		assertThat(comparison.to()).isEqualTo(date("2026-09-09"));
		assertThat(comparison.currentPeriod().to()).isEqualTo(date("2026-10-09"));
		assertThat(comparison.currentAmount()).isEqualTo(112000);
		assertThat(comparison.totalAmount()).isEqualTo(100000);
		assertThat(comparison.changeAmount()).isEqualTo(12000);
		assertThat(comparison.changeRate()).isEqualByComparingTo("12.00");
		assertThat(comparison.samePoint()).isTrue();
	}

	@Test
	void followsConfiguredMonthlyStartDayRatherThanCalendarDates() {
		var ledger = new Ledger("장부", 500000);
		ledger.changeBudgetCycleStartDay(15);
		var result = statistics(ledger, "2026-09-15", "2026-10-14", "2026-10-09T03:00:00Z", Map.of());
		assertThat(result.comparison().from()).isEqualTo(date("2026-08-15"));
		assertThat(result.comparison().to()).isEqualTo(date("2026-09-08"));
		assertThat(result.comparison().currentPeriod().to()).isEqualTo(date("2026-10-09"));
		assertThat(result.comparison().changeRate()).isNull();
	}

	@Test
	void followsWeeklyStartWeekdayAndUsesTheSameElapsedDays() {
		var ledger = new Ledger("장부", 500000);
		ledger.changeBudgetCycle(BudgetCycleUnit.WEEKLY, 1, 4, 200000L);
		var result = statistics(ledger, "2026-10-08", "2026-10-14", "2026-10-09T03:00:00Z", Map.of(
			date("2026-10-01"), 1000L, date("2026-10-03"), 9000L, date("2026-10-08"), 2000L));
		assertThat(result.comparison().from()).isEqualTo(date("2026-10-01"));
		assertThat(result.comparison().to()).isEqualTo(date("2026-10-02"));
		assertThat(result.comparison().totalAmount()).isEqualTo(1000);
		assertThat(result.comparison().changeAmount()).isEqualTo(1000);
	}

	@Test
	void retainsFullCycleComparisonForCompletedPeriods() {
		var result = statistics(new Ledger("장부", 500000), "2026-09-01", "2026-09-30",
			"2026-10-09T03:00:00Z", Map.of(date("2026-09-30"), 2000L, date("2026-08-31"), 1000L));
		assertThat(result.comparison().from()).isEqualTo(date("2026-08-01"));
		assertThat(result.comparison().to()).isEqualTo(date("2026-08-31"));
		assertThat(result.comparison().currentAmount()).isEqualTo(2000);
		assertThat(result.comparison().samePoint()).isFalse();
	}

	@Test
	void doesNotSpillPastTheEndOfAShorterPreviousMonth() {
		var result = statistics(new Ledger("장부", 500000), "2028-03-01", "2028-03-31",
			"2028-03-30T03:00:00Z", Map.of(date("2028-02-29"), 1000L, date("2028-03-01"), 2000L));
		assertThat(result.comparison().from()).isEqualTo(date("2028-02-01"));
		assertThat(result.comparison().to()).isEqualTo(date("2028-02-29"));
		assertThat(result.comparison().totalAmount()).isEqualTo(1000);
		assertThat(result.comparison().currentPeriod().to()).isEqualTo(date("2028-03-30"));
	}

	@Test
	void clipsYearAndCustomPeriodsAfterResolvingTheirOriginalComparisonWindows() {
		var ledger = new Ledger("장부", 500000);
		var year = statistics(ledger, "2026-01-01", "2026-12-31", "2026-01-09T03:00:00Z", Map.of());
		assertThat(year.comparison().from()).isEqualTo(date("2025-01-01"));
		assertThat(year.comparison().to()).isEqualTo(date("2025-01-09"));
		var custom = statistics(ledger, "2026-10-03", "2026-10-12", "2026-10-05T03:00:00Z", Map.of());
		assertThat(custom.comparison().from()).isEqualTo(date("2026-09-23"));
		assertThat(custom.comparison().to()).isEqualTo(date("2026-09-25"));
	}

	@Test
	void marksFuturePeriodsUnavailableRatherThanComparingZeroAgainstPastSpending() {
		var result = statistics(new Ledger("장부", 500000), "2026-11-01", "2026-11-30",
			"2026-10-09T03:00:00Z", Map.of(date("2026-10-09"), 1000L));
		assertThat(result.comparison().available()).isFalse();
		assertThat(result.comparison().currentPeriod()).isNull();
		assertThat(result.comparison().changeRate()).isNull();
	}

	private StatisticsSnapshot statistics(Ledger ledger, String from, String to, String instant, Map<LocalDate, Long> expenses) {
		var access = mock(LedgerAccessService.class);
		var budget = mock(BudgetService.class);
		var jdbc = mock(JdbcTemplate.class);
		UUID userId = UUID.randomUUID();
		when(access.requireActiveMembership(userId)).thenReturn(new LedgerMember(ledger.getId(), userId, MemberRole.MEMBER));
		when(access.requireLedger(ledger.getId())).thenReturn(ledger);
		when(budget.proratedBudget(eq(ledger.getId()), any(LocalDate.class), any(LocalDate.class))).thenReturn(500000L);
		when(jdbc.queryForObject(anyString(), eq(Long.class), eq(ledger.getId()), any(LocalDate.class), any(LocalDate.class)))
			.thenAnswer(invocation -> {
				LocalDate rangeFrom = invocation.getArgument(3);
				LocalDate rangeTo = invocation.getArgument(4);
				return expenses.entrySet().stream().filter(entry -> !entry.getKey().isBefore(rangeFrom) && !entry.getKey().isAfter(rangeTo))
					.mapToLong(Map.Entry::getValue).sum();
			});
		var service = new AnalyticsService(access, budget, mock(CategoryRepository.class), jdbc,
			Clock.fixed(Instant.parse(instant), ZoneOffset.UTC));
		return service.statistics(userId, null, date(from), date(to));
	}

	private static LocalDate date(String value) {
		return LocalDate.parse(value);
	}
}
