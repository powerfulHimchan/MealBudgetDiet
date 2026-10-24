package com.mealbudgetdiet.budget.application;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.YearMonth;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.mealbudgetdiet.budget.domain.BudgetCycle;
import com.mealbudgetdiet.budget.domain.BudgetCycleUnit;
import com.mealbudgetdiet.budget.infrastructure.MonthlyBudgetRepository;
import com.mealbudgetdiet.ledger.application.LedgerAccessService;
import com.mealbudgetdiet.ledger.domain.Ledger;
import com.mealbudgetdiet.ledger.domain.MemberRole;
import com.mealbudgetdiet.ledger.infrastructure.LedgerRepository;
import com.mealbudgetdiet.shared.api.ApiException;

/** Replays immutable policy boundaries against current expense totals. Reads never write or call AI. */
@Service
public class BudgetRolloverService {
	private final JdbcTemplate jdbc;
	private final MonthlyBudgetRepository monthlyBudgets;
	private final LedgerAccessService access;
	private final LedgerRepository ledgers;
	private final Clock clock;

	public BudgetRolloverService(JdbcTemplate jdbc, MonthlyBudgetRepository monthlyBudgets,
		LedgerAccessService access, LedgerRepository ledgers, Clock clock) {
		this.jdbc = jdbc;
		this.monthlyBudgets = monthlyBudgets;
		this.access = access;
		this.ledgers = ledgers;
		this.clock = clock;
	}

	@Transactional(readOnly = true)
	public Settings settings(UUID userId) {
		return settings(access.requireLedger(access.requireActiveMembership(userId).getId().getLedgerId()));
	}

	@Transactional
	public Settings update(UUID userId, boolean surplus, boolean deficit, int version) {
		var membership = access.requireActiveMembership(userId);
		if (membership.getRole() != MemberRole.ADMIN) {
			throw new ApiException(HttpStatus.FORBIDDEN, "ADMIN_REQUIRED", "관리자 권한이 필요합니다.");
		}
		var ledger = ledgers.findByIdForUpdate(membership.getId().getLedgerId()).orElseThrow();
		if (ledger.getVersion() != version) {
			throw new ApiException(HttpStatus.CONFLICT, "LEDGER_VERSION_CONFLICT", "다른 사용자가 장부 설정을 먼저 변경했습니다.");
		}
		if (ledger.isCarrySurplus() != surplus || ledger.isCarryDeficit() != deficit) {
			BudgetCycle current = cycle(ledger, today());
			var active = ruleAt(rules(ledger.getId()), current.from());
			boolean reset = active == null || !active.matches(ledger) || !active.enabled();
			ensureBase(ledger, current.from());
			writeRule(ledger, current, surplus, deficit, reset);
			ledger.changeRollover(surplus, deficit);
			ledgers.flush();
		}
		return settings(ledger);
	}

	private Settings settings(Ledger ledger) {
		var rows = rules(ledger.getId());
		Rule latest = rows.isEmpty() ? null : rows.getLast();
		LocalDate effective = latest == null ? cycle(ledger, today()).to().plusDays(1) : latest.effective();
		return new Settings(ledger.isCarrySurplus(), ledger.isCarryDeficit(), effective,
			latest != null && effective.isAfter(today()), ledger.getVersion());
	}

	/** A new calendar starts a fresh chain, even when switching back to an older calendar. */
	public void resetCalendar(Ledger ledger) {
		if (rules(ledger.getId()).isEmpty()) return;
		jdbc.update("delete from budget_rollover_rules where ledger_id = ? and effective_from > ?", ledger.getId(), today());
		BudgetCycle current = cycle(ledger, today());
		ensureBase(ledger, current.from());
		writeRule(ledger, current, ledger.isCarrySurplus(), ledger.isCarryDeficit(), true);
	}

	/** Keep earlier default amounts stable; explicit historical monthly overrides remain editable. */
	public void recordBaseChange(Ledger ledger) {
		if (rules(ledger.getId()).isEmpty()) return;
		writeBase(ledger, cycle(ledger, today()).from(), false);
	}

	private void ensureBase(Ledger ledger, LocalDate from) {
		writeBase(ledger, from, true);
	}

	private void writeBase(Ledger ledger, LocalDate from, boolean preserveExisting) {
		String conflict = preserveExisting ? "do nothing" : "do update set amount = excluded.amount";
		jdbc.update("""
			insert into budget_rollover_bases (ledger_id, effective_from, cycle_unit, month_start_day, week_start_day, amount)
			values (?, ?, ?, ?, ?, ?) on conflict (ledger_id, cycle_unit, month_start_day, week_start_day, effective_from)
			""" + conflict, ledger.getId(), from, ledger.getBudgetCycleUnit().name(), ledger.getBudgetCycleStartDay(),
			ledger.getBudgetWeekStartDay(), defaultAmount(ledger));
	}

	private void writeRule(Ledger ledger, BudgetCycle current, boolean surplus, boolean deficit, boolean reset) {
		jdbc.update("""
			insert into budget_rollover_rules (ledger_id, effective_from, seed_from, cycle_unit, month_start_day,
			  week_start_day, carry_surplus, carry_deficit, reset_chain) values (?, ?, ?, ?, ?, ?, ?, ?, ?)
			on conflict (ledger_id, effective_from) do update set seed_from = excluded.seed_from,
			  cycle_unit = excluded.cycle_unit, month_start_day = excluded.month_start_day,
			  week_start_day = excluded.week_start_day, carry_surplus = excluded.carry_surplus,
			  carry_deficit = excluded.carry_deficit, reset_chain = excluded.reset_chain
			""", ledger.getId(), current.to().plusDays(1), current.from(), ledger.getBudgetCycleUnit().name(),
			ledger.getBudgetCycleStartDay(), ledger.getBudgetWeekStartDay(), surplus, deficit, reset);
	}

	public BudgetRolloverSnapshot forCycle(Ledger ledger, BudgetCycle target, long fallbackBase) {
		List<Rule> history = rules(ledger.getId());
		TreeMap<LocalDate, Long> bases = defaultHistory(ledger, target.from());
		var priorBase = bases.floorEntry(target.from());
		if (priorBase != null && (ledger.getBudgetCycleUnit() == BudgetCycleUnit.WEEKLY
			|| monthlyBudgets.findByLedgerIdAndBudgetMonth(ledger.getId(), target.yearMonth().atDay(1)).isEmpty())) {
			fallbackBase = priorBase.getValue();
		}
		Rule targetRule = ruleAt(history, target.from());
		if (targetRule == null || !targetRule.matches(ledger) || !targetRule.enabled()) {
			return BudgetRolloverSnapshot.disabled(fallbackBase);
		}
		Rule epoch = null;
		for (Rule rule : history) {
			if (!rule.effective().isAfter(target.from()) && rule.reset()) epoch = rule;
		}
		if (epoch == null || !epoch.matches(ledger)) return BudgetRolloverSnapshot.disabled(fallbackBase);
		BudgetCycle first = cycle(ledger, epoch.seed());
		// Bound arbitrary future queries while allowing over a century of weekly history.
		if (java.time.temporal.ChronoUnit.DAYS.between(first.from(), target.from()) > 36600) {
			throw new ApiException(HttpStatus.BAD_REQUEST, "BUDGET_PERIOD_TOO_LARGE", "이월 예산 조회 기간이 너무 깁니다.");
		}
		Map<YearMonth, Long> overrides = ledger.getBudgetCycleUnit() == BudgetCycleUnit.MONTHLY
			? monthlyBudgets.findAllByLedgerIdAndBudgetMonthBetween(ledger.getId(), first.yearMonth().atDay(1), target.yearMonth().atDay(1))
				.stream().collect(Collectors.toMap(b -> YearMonth.from(b.getBudgetMonth()), b -> b.getAmount())) : Map.of();
		TreeMap<LocalDate, Long> expenses = new TreeMap<>();
		jdbc.query("""
			select spent_on, sum(amount) from expenses where ledger_id = ? and spent_on >= ? and spent_on < ?
			group by spent_on
			""", rs -> { expenses.put(rs.getObject(1, LocalDate.class), rs.getLong(2)); }, ledger.getId(), first.from(), target.from());
		long balance = 0;
		for (BudgetCycle current = first; !current.from().isAfter(target.from()); current = cycle(ledger, current.to().plusDays(1))) {
			Rule rule = ruleAt(history, current.from());
			boolean enabled = rule != null && rule.matches(ledger) && rule.enabled();
			long carry = enabled ? carry(balance, rule.surplus(), rule.deficit()) : 0;
			if (rule != null && rule.reset() && current.from().equals(rule.effective()) && !rule.equals(epoch)) carry = 0;
			var baseEntry = bases.floorEntry(current.from());
			long base = overrides.getOrDefault(current.yearMonth(), baseEntry == null ? defaultAmount(ledger) : baseEntry.getValue());
			long effective = Math.addExact(base, carry);
			if (current.from().equals(target.from())) return new BudgetRolloverSnapshot(enabled, base, carry, effective);
			long spent = expenses.subMap(current.from(), true, current.to(), true).values().stream().reduce(0L, Math::addExact);
			balance = Math.subtractExact(effective, spent);
		}
		return BudgetRolloverSnapshot.disabled(fallbackBase);
	}

	public TreeMap<LocalDate, Long> defaultHistory(Ledger ledger, LocalDate through) {
		TreeMap<LocalDate, Long> bases = new TreeMap<>();
		jdbc.query("""
			select effective_from, amount from budget_rollover_bases where ledger_id = ? and cycle_unit = ?
			and month_start_day = ? and week_start_day = ? and effective_from <= ? order by effective_from
			""", rs -> { bases.put(rs.getObject(1, LocalDate.class), rs.getLong(2)); }, ledger.getId(),
			ledger.getBudgetCycleUnit().name(), ledger.getBudgetCycleStartDay(), ledger.getBudgetWeekStartDay(), through);
		return bases;
	}

	static long carry(long balance, boolean surplus, boolean deficit) {
		return (balance > 0 && surplus) || (balance < 0 && deficit) ? balance : 0;
	}

	private List<Rule> rules(UUID ledgerId) {
		return jdbc.query("select * from budget_rollover_rules where ledger_id = ? order by effective_from",
			(rs, n) -> new Rule(rs.getObject("effective_from", LocalDate.class), rs.getObject("seed_from", LocalDate.class),
				BudgetCycleUnit.valueOf(rs.getString("cycle_unit")), rs.getInt("month_start_day"), rs.getInt("week_start_day"),
				rs.getBoolean("carry_surplus"), rs.getBoolean("carry_deficit"), rs.getBoolean("reset_chain")), ledgerId);
	}

	private static Rule ruleAt(List<Rule> rules, LocalDate date) {
		Rule result = null;
		for (Rule rule : rules) {
			if (rule.effective().isAfter(date)) break;
			result = rule;
		}
		return result;
	}

	private static long defaultAmount(Ledger ledger) {
		return ledger.getBudgetCycleUnit() == BudgetCycleUnit.WEEKLY ? ledger.getDefaultWeeklyBudget() : ledger.getDefaultMonthlyBudget();
	}
	private static BudgetCycle cycle(Ledger ledger, LocalDate date) {
		return BudgetCycle.containing(date, ledger.getBudgetCycleUnit(), ledger.getBudgetCycleStartDay(), ledger.getBudgetWeekStartDay());
	}
	private LocalDate today() { return LocalDate.now(clock.withZone(ZoneId.of("Asia/Seoul"))); }

	private record Rule(LocalDate effective, LocalDate seed, BudgetCycleUnit unit, int monthStart, int weekStart,
		boolean surplus, boolean deficit, boolean reset) {
		boolean enabled() { return surplus || deficit; }
		boolean matches(Ledger ledger) {
			return unit == ledger.getBudgetCycleUnit() && monthStart == ledger.getBudgetCycleStartDay()
				&& weekStart == ledger.getBudgetWeekStartDay();
		}
	}
	public record Settings(boolean carrySurplus, boolean carryDeficit, LocalDate effectiveFrom, boolean pending, int version) { }
}
