package com.mealbudgetdiet.budget.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;

import com.mealbudgetdiet.TestcontainersConfiguration;
import com.mealbudgetdiet.analytics.application.AnalyticsService;
import com.mealbudgetdiet.budget.domain.BudgetCycle;
import com.mealbudgetdiet.budget.domain.BudgetCycleUnit;
import com.mealbudgetdiet.budget.domain.MonthlyBudget;
import com.mealbudgetdiet.budget.infrastructure.MonthlyBudgetRepository;
import com.mealbudgetdiet.ledger.application.LedgerAccessService;
import com.mealbudgetdiet.ledger.application.LedgerSettingsService;
import com.mealbudgetdiet.ledger.domain.Ledger;
import com.mealbudgetdiet.ledger.domain.LedgerMember;
import com.mealbudgetdiet.ledger.domain.MemberRole;
import com.mealbudgetdiet.ledger.infrastructure.LedgerRepository;
import com.mealbudgetdiet.shared.api.ApiException;

@SpringBootTest
@Import({TestcontainersConfiguration.class, BudgetRolloverIntegrationTest.Config.class})
@Transactional
class BudgetRolloverIntegrationTest {
	@Autowired BudgetRolloverService rollover;
	@Autowired BudgetService budgets;
	@Autowired LedgerSettingsService settings;
	@Autowired AnalyticsService analytics;
	@Autowired LedgerRepository ledgers;
	@Autowired MonthlyBudgetRepository monthly;
	@Autowired JdbcTemplate jdbc;
	@Autowired MutableClock clock;
	@MockitoBean LedgerAccessService access;
	Ledger ledger;
	UUID user;

	@BeforeEach void setup() {
		clock.date("2026-10-24");
		ledger = ledgers.saveAndFlush(new Ledger("이월 검증", 800000));
		user = UUID.randomUUID();
		when(access.requireLedger(ledger.getId())).thenReturn(ledger);
		when(access.requireActiveMembership(user)).thenReturn(new LedgerMember(ledger.getId(), user, MemberRole.ADMIN));
	}

	@Test void disabledKeepsExistingBudgetAndHasNoBreakdown() {
		expense("2026-09-20", 2000000);
		var result = budget("2026-11-01");
		assertThat(result.appliedAmount()).isEqualTo(800000);
		assertThat(result.rollover().enabled()).isFalse();
		assertThat(rollover.settings(user).carrySurplus()).isFalse();
		assertThat(rollover.settings(user).carryDeficit()).isFalse();
	}

	@Test void startsNextCycleAccumulatesAndReplaysAfterHistoricalEditsWithoutOtherLedgerData() {
		expense("2026-09-10", 3000000);
		expense("2026-10-10", 700000);
		expense("2026-11-10", 750000);
		Ledger other = ledgers.saveAndFlush(new Ledger("다른 장부", 800000));
		insertExpense(other.getId(), "2026-10-10", 9000000);
		var saved = rollover.update(user, true, true, ledger.getVersion());
		assertThat(saved.pending()).isTrue();
		assertThat(saved.effectiveFrom()).isEqualTo(LocalDate.parse("2026-11-01"));
		assertThat(budget("2026-10-01").rollover().enabled()).isFalse();
		assertThat(budget("2026-11-01").appliedAmount()).isEqualTo(900000);
		assertThat(budget("2026-12-01").appliedAmount()).isEqualTo(950000);
		jdbc.update("update expenses set amount = 900000 where ledger_id = ? and spent_on = '2026-10-10'", ledger.getId());
		assertThat(budget("2026-11-01").appliedAmount()).isEqualTo(700000);
		assertThat(budget("2026-12-01").appliedAmount()).isEqualTo(750000);
		jdbc.update("delete from expenses where ledger_id = ? and spent_on = '2026-10-10'", ledger.getId());
		assertThat(budget("2026-11-01").appliedAmount()).isEqualTo(1600000);
	}

	@Test void surplusAndDeficitAreIndependentlySelectableAndPendingChangesReplaceOneAnother() {
		expense("2026-10-10", 700000);
		rollover.update(user, false, true, ledger.getVersion());
		assertThat(budget("2026-11-01").appliedAmount()).isEqualTo(800000);
		rollover.update(user, true, false, ledger.getVersion());
		assertThat(budget("2026-11-01").appliedAmount()).isEqualTo(900000);
		jdbc.update("update expenses set amount = 900000 where ledger_id = ?", ledger.getId());
		assertThat(budget("2026-11-01").appliedAmount()).isEqualTo(800000);
		rollover.update(user, false, true, ledger.getVersion());
		assertThat(budget("2026-11-01").appliedAmount()).isEqualTo(700000);
		assertThat(jdbc.queryForObject("select count(*) from budget_rollover_rules where ledger_id = ?", Integer.class, ledger.getId())).isEqualTo(1);
	}

	@Test void preservesDebtBeyondOneFullBudgetAndAvoidsZeroDivision() {
		expense("2026-10-10", 2400000);
		rollover.update(user, true, true, ledger.getVersion());
		assertThat(budget("2026-11-01").appliedAmount()).isEqualTo(-800000);
		assertThat(budget("2026-12-01").appliedAmount()).isZero();
		assertThat(budget("2027-01-01").appliedAmount()).isEqualTo(800000);
		clock.date("2026-11-10");
		assertThat(analytics.dashboard(user, null, 5).remaining()).isEqualTo(-800000);
		assertThat(analytics.dashboard(user, null, 5).usageRate().intValue()).isEqualTo(100);
		clock.date("2026-12-10");
		assertThat(analytics.dashboard(user, null, 5).usageRate().intValue()).isZero();
	}

	@Test void stoppingIsDeferredPreservesHistoryAndReenablingStartsFresh() {
		expense("2026-10-10", 700000);
		rollover.update(user, true, true, ledger.getVersion());
		clock.date("2026-11-10");
		rollover.update(user, false, false, ledger.getVersion());
		assertThat(budget("2026-11-01").appliedAmount()).isEqualTo(900000);
		assertThat(budget("2026-12-01").rollover().enabled()).isFalse();
		clock.date("2026-12-10");
		rollover.update(user, true, true, ledger.getVersion());
		assertThat(budget("2027-01-01").appliedAmount()).isEqualTo(1600000);
		assertThat(budget("2026-11-01").appliedAmount()).isEqualTo(900000);
	}

	@Test void usesMonthlyOverridesAndFreezesEarlierDefaultAmounts() {
		expense("2026-10-10", 700000);
		rollover.update(user, true, true, ledger.getVersion());
		monthly.saveAndFlush(new MonthlyBudget(ledger.getId(), YearMonth.of(2026, 11), 1000000));
		clock.date("2026-11-10");
		budgets.updateDefault(user, 900000, ledger.getVersion());
		assertThat(budget("2026-11-01").appliedAmount()).isEqualTo(1100000);
		assertThat(budget("2026-11-01").rollover().baseAmount()).isEqualTo(1000000);
		assertThat(budget("2026-12-01").appliedAmount()).isEqualTo(2000000);
	}

	@Test void weeklyCyclesCrossMonthsAndCalendarChangesStartAnIndependentChain() {
		settings.updateBudgetCycle(user, BudgetCycleUnit.WEEKLY, 1, 1, 100000L, ledger.getVersion());
		expense("2026-10-20", 70000);
		expense("2026-10-28", 200000);
		rollover.update(user, true, true, ledger.getVersion());
		assertThat(budget("2026-10-26").appliedAmount()).isEqualTo(130000);
		assertThat(budget("2026-11-02").appliedAmount()).isEqualTo(30000);
		clock.date("2026-11-10");
		settings.updateBudgetCycle(user, BudgetCycleUnit.MONTHLY, 25, 1, 100000L, ledger.getVersion());
		assertThat(budget("2026-11-10").rollover().enabled()).isFalse();
		assertThat(budget("2026-11-25").appliedAmount()).isEqualTo(1400000);
	}

	@Test void handlesMonthEndAndLeapYears() {
		clock.date("2028-01-31");
		settings.updateBudgetCycle(user, BudgetCycleUnit.MONTHLY, 31, 1, null, ledger.getVersion());
		expense("2028-02-10", 100000);
		assertThat(rollover.update(user, true, true, ledger.getVersion()).effectiveFrom()).isEqualTo(LocalDate.parse("2028-02-29"));
		assertThat(budget("2028-02-29").appliedAmount()).isEqualTo(1500000);
	}

	@Test void multiCycleStatisticsIncludeOnlyOpeningCarryAndSingleCycleUsesAppliedBudget() {
		expense("2026-10-10", 700000);
		rollover.update(user, true, true, ledger.getVersion());
		assertThat(budgets.proratedBudget(ledger.getId(), LocalDate.parse("2026-10-01"), LocalDate.parse("2026-12-31"))).isEqualTo(2400000);
		assertThat(budgets.proratedBudget(ledger.getId(), LocalDate.parse("2026-11-01"), LocalDate.parse("2026-12-31"))).isEqualTo(1700000);
		assertThat(budgets.proratedBudget(ledger.getId(), LocalDate.parse("2026-11-01"), LocalDate.parse("2026-11-30"))).isEqualTo(900000);
		assertThat(budgets.proratedBudget(ledger.getId(), LocalDate.parse("2026-11-01"), LocalDate.parse("2026-11-15"))).isEqualTo(450000);
	}

	@Test void rejectsStaleSettingsAndNonAdmins() {
		rollover.update(user, true, true, ledger.getVersion());
		assertThatThrownBy(() -> rollover.update(user, false, false, 0)).isInstanceOf(ApiException.class);
		when(access.requireActiveMembership(user)).thenReturn(new LedgerMember(ledger.getId(), user, MemberRole.MEMBER));
		assertThatThrownBy(() -> rollover.update(user, false, false, ledger.getVersion())).isInstanceOf(ApiException.class);
	}

	private BudgetSnapshot budget(String date) {
		return budgets.budgetForCycle(ledger, BudgetCycle.containing(LocalDate.parse(date), ledger.getBudgetCycleUnit(),
			ledger.getBudgetCycleStartDay(), ledger.getBudgetWeekStartDay()));
	}
	private void expense(String date, long amount) { insertExpense(ledger.getId(), date, amount); }
	private void insertExpense(UUID ledgerId, String date, long amount) {
		jdbc.update("""
			insert into expenses (id, ledger_id, amount, spent_on, version, created_at, updated_at)
			values (?, ?, ?, ?, 0, now(), now())
			""", UUID.randomUUID(), ledgerId, amount, LocalDate.parse(date));
	}
	@TestConfiguration(proxyBeanMethods = false)
	static class Config {
		@Bean @Primary MutableClock rolloverClock() { return new MutableClock(); }
	}
	static class MutableClock extends Clock {
		private Instant instant = Instant.parse("2026-10-24T03:00:00Z");
		void date(String date) { instant = Instant.parse(date + "T03:00:00Z"); }
		@Override public Instant instant() { return instant; }
		@Override public ZoneId getZone() { return ZoneId.of("Asia/Seoul"); }
		@Override public Clock withZone(ZoneId zone) { return Clock.fixed(instant, zone); }
	}
}
