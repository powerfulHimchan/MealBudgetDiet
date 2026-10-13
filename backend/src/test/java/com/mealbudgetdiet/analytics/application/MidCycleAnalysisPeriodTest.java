package com.mealbudgetdiet.analytics.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import java.time.LocalDate;
import java.time.YearMonth;
import org.junit.jupiter.api.Test;
import com.mealbudgetdiet.budget.domain.BudgetCycle;
import com.mealbudgetdiet.shared.api.ApiException;

class MidCycleAnalysisPeriodTest {
	@Test void supportsOddEvenAndLeapMonths() {
		for (var fixture : new String[][] {
			{"2026-10", "2026-10-16"}, {"2026-09", "2026-09-15"},
			{"2026-02", "2026-02-14"}, {"2028-02", "2028-02-15"}
		}) {
			var cycle = BudgetCycle.starting(YearMonth.parse(fixture[0]), 1);
			var midpoint = LocalDate.parse(fixture[1]);
			assertThat(MidCycleAnalysisPeriod.midpoint(cycle)).isEqualTo(midpoint);
			assertThat(MidCycleAnalysisPeriod.eligible(cycle, midpoint.minusDays(1))).isFalse();
			assertThat(MidCycleAnalysisPeriod.eligible(cycle, midpoint)).isTrue();
			assertThat(MidCycleAnalysisPeriod.eligible(cycle, cycle.to())).isTrue();
			assertThat(MidCycleAnalysisPeriod.eligible(cycle, cycle.to().plusDays(1))).isFalse();
		}
	}

	@Test void supportsCustomMonthlyAndWeeklyStarts() {
		var month = BudgetCycle.starting(YearMonth.parse("2026-09"), 25);
		assertThat(MidCycleAnalysisPeriod.midpoint(month)).isEqualTo(LocalDate.parse("2026-10-09"));
		var week = BudgetCycle.weeklyContaining(LocalDate.parse("2026-10-21"), 1);
		assertThat(MidCycleAnalysisPeriod.midpoint(week)).isEqualTo(LocalDate.parse("2026-10-22"));
		assertThat(MidCycleAnalysisPeriod.eligible(week, LocalDate.parse("2026-10-21"))).isFalse();
		assertThat(MidCycleAnalysisPeriod.eligible(week, LocalDate.parse("2026-10-22"))).isTrue();
	}

	@Test void preservesAboutOneHundredCharactersAndLimitsUnicodeSafely() {
		assertThat(MidCycleAnalysisService.normalizeSummary("가".repeat(100))).hasSize(100);
		String capped = MidCycleAnalysisService.normalizeSummary("😀".repeat(120));
		assertThat(capped.codePointCount(0, capped.length())).isEqualTo(110);
		assertThat(capped).endsWith("…");
		assertThat(MidCycleAnalysisService.normalizeSummary("  장보기\n중심 소비  ")).isEqualTo("장보기 중심 소비");
		assertThatThrownBy(() -> MidCycleAnalysisService.normalizeSummary("   ")).isInstanceOf(ApiException.class);
	}
}
