package com.mealbudgetdiet.budget.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.time.YearMonth;

import org.junit.jupiter.api.Test;

class BudgetCycleTest {

	@Test
	void usesCalendarMonthWhenStartDayIsOne() {
		var cycle = BudgetCycle.starting(YearMonth.of(2026, 9), 1);

		assertThat(cycle.from()).isEqualTo(LocalDate.of(2026, 9, 1));
		assertThat(cycle.to()).isEqualTo(LocalDate.of(2026, 9, 30));
		assertThat(cycle.days()).isEqualTo(30);
	}

	@Test
	void findsCycleContainingDateBeforeNextStartDay() {
		var cycle = BudgetCycle.containing(LocalDate.of(2026, 10, 5), 25);

		assertThat(cycle.yearMonth()).isEqualTo(YearMonth.of(2026, 9));
		assertThat(cycle.from()).isEqualTo(LocalDate.of(2026, 9, 25));
		assertThat(cycle.to()).isEqualTo(LocalDate.of(2026, 10, 24));
	}

	@Test
	void clampsStartDayToLastDayOfShortMonth() {
		var january = BudgetCycle.starting(YearMonth.of(2026, 1), 31);
		var february = BudgetCycle.starting(YearMonth.of(2026, 2), 31);

		assertThat(january.from()).isEqualTo(LocalDate.of(2026, 1, 31));
		assertThat(january.to()).isEqualTo(LocalDate.of(2026, 2, 27));
		assertThat(february.from()).isEqualTo(LocalDate.of(2026, 2, 28));
		assertThat(february.to()).isEqualTo(LocalDate.of(2026, 3, 30));
	}

	@Test
	void startsWeeklyCycleOnSelectedWeekdayAcrossMonthAndYear() {
		var cycle = BudgetCycle.weeklyContaining(LocalDate.of(2027, 1, 1), 4);
		assertThat(cycle.from()).isEqualTo(LocalDate.of(2026, 12, 31));
		assertThat(cycle.to()).isEqualTo(LocalDate.of(2027, 1, 6));
		assertThat(cycle.days()).isEqualTo(7);
	}
}
