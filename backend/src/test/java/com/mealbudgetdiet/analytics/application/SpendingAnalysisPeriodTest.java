package com.mealbudgetdiet.analytics.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;
import com.mealbudgetdiet.budget.domain.BudgetCycleUnit;
import com.mealbudgetdiet.ledger.domain.Ledger;
import com.mealbudgetdiet.shared.api.ApiException;

class SpendingAnalysisPeriodTest {
	@Test void requiresOneCompleteMonthlyCycleAndHonorsLeapMonthEnds() {
		var ledger = new Ledger("장부", 500000);
		ledger.changeBudgetCycleStartDay(31);
		assertThat(SpendingAnalysisPeriod.eligible(ledger, date("2028-02-29"), date("2028-03-30"), date("2028-03-31"))).isTrue();
		assertThat(SpendingAnalysisPeriod.eligible(ledger, date("2028-02-29"), date("2028-03-30"), date("2028-03-30"))).isFalse();
		assertThat(SpendingAnalysisPeriod.eligible(ledger, date("2028-02-29"), date("2028-03-29"), date("2028-03-31"))).isFalse();
	}
	@Test void requiresOneCompletedWeeklyCycle() {
		var ledger = new Ledger("장부", 500000);
		ledger.changeBudgetCycle(BudgetCycleUnit.WEEKLY, 1, 4, 200000L);
		assertThat(SpendingAnalysisPeriod.eligible(ledger, date("2026-10-01"), date("2026-10-07"), date("2026-10-08"))).isTrue();
		assertThat(SpendingAnalysisPeriod.eligible(ledger, date("2026-10-01"), date("2026-10-06"), date("2026-10-08"))).isFalse();
		assertThat(SpendingAnalysisPeriod.eligible(ledger, date("2026-10-08"), date("2026-10-14"), date("2026-10-09"))).isFalse();
	}
	@Test void capsUnicodeTextWithoutSplittingSurrogatePairs() {
		String normalized = SpendingAnalysisService.normalizeSummary("😀".repeat(330));
		assertThat(normalized.codePointCount(0, normalized.length())).isEqualTo(320);
		assertThat(normalized).endsWith("…");
	}
	@Test void preservesThreeHundredCharacterSummaryAndExactLimit() {
		String summary = "가".repeat(300);
		assertThat(SpendingAnalysisService.normalizeSummary(summary)).isEqualTo(summary);
		String atLimit = "가".repeat(320);
		assertThat(SpendingAnalysisService.normalizeSummary(atLimit)).isEqualTo(atLimit);
	}
	@Test void normalizesWhitespaceAndRejectsEmptyOutput() {
		assertThat(SpendingAnalysisService.normalizeSummary("  장보기\n\t중심 소비  ")).isEqualTo("장보기 중심 소비");
		assertThatThrownBy(() -> SpendingAnalysisService.normalizeSummary("   ")).isInstanceOf(ApiException.class);
	}
	private static LocalDate date(String value) { return LocalDate.parse(value); }
}
