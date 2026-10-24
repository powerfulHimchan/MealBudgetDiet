package com.mealbudgetdiet.budget.application;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.Clock;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.mealbudgetdiet.budget.domain.BudgetCycle;
import com.mealbudgetdiet.budget.domain.BudgetCycleUnit;
import com.mealbudgetdiet.budget.domain.MonthlyBudget;
import com.mealbudgetdiet.budget.infrastructure.MonthlyBudgetRepository;
import com.mealbudgetdiet.ledger.application.LedgerAccessService;
import com.mealbudgetdiet.ledger.domain.Ledger;
import com.mealbudgetdiet.ledger.domain.MemberRole;
import com.mealbudgetdiet.ledger.infrastructure.LedgerRepository;
import com.mealbudgetdiet.shared.api.ApiException;

@Service
public class BudgetService {

	private final LedgerAccessService ledgerAccessService;
	private final LedgerRepository ledgerRepository;
	private final MonthlyBudgetRepository monthlyBudgetRepository;
	private final Clock clock;
	private final BudgetRolloverService rollover;

	public BudgetService(
		LedgerAccessService ledgerAccessService,
		LedgerRepository ledgerRepository,
		MonthlyBudgetRepository monthlyBudgetRepository,
		Clock clock,
		BudgetRolloverService rollover
	) {
		this.ledgerAccessService = ledgerAccessService;
		this.ledgerRepository = ledgerRepository;
		this.monthlyBudgetRepository = monthlyBudgetRepository;
		this.clock = clock;
		this.rollover = rollover;
	}

	@Transactional(readOnly = true)
	public BudgetSnapshot getCurrentBudget(UUID userId) {
		var ledger = ledgerAccessService.requireLedger(ledgerId(userId));
		var cycle = BudgetCycle.containing(LocalDate.now(clock.withZone(ZoneId.of("Asia/Seoul"))),
			ledger.getBudgetCycleUnit(), ledger.getBudgetCycleStartDay(), ledger.getBudgetWeekStartDay());
		return budgetForCycle(ledger, cycle);
	}

	public BudgetSnapshot budgetForCycle(Ledger ledger, BudgetCycle cycle) {
		BudgetSnapshot base;
		if (ledger.getBudgetCycleUnit() == BudgetCycleUnit.WEEKLY) {
			base = new BudgetSnapshot(cycle.yearMonth(), cycle, ledger.getDefaultWeeklyBudget(),
				BudgetSource.WEEKLY_DEFAULT, ledger.getVersion());
		} else {
			base = appliedBudget(ledger, cycle);
		}
		var applied = rollover.forCycle(ledger, cycle, base.amount());
		return new BudgetSnapshot(base.yearMonth(), cycle, base.amount(), base.source(), base.version(), applied);
	}

	@Transactional(readOnly = true)
	public BudgetSnapshot getAppliedBudget(UUID userId, YearMonth yearMonth) {
		UUID ledgerId = ledgerId(userId);
		var ledger = ledgerAccessService.requireLedger(ledgerId);
		return ledger.getBudgetCycleUnit() == BudgetCycleUnit.WEEKLY
			? appliedBudget(ledger, BudgetCycle.starting(yearMonth, ledger.getBudgetCycleStartDay()))
			: budgetForCycle(ledger, BudgetCycle.starting(yearMonth, ledger.getBudgetCycleStartDay()));
	}

	@Transactional
	public BudgetSnapshot updateDefault(UUID userId, long amount, int version) {
		UUID ledgerId = requireAdmin(userId);
		var ledger = ledgerAccessService.requireLedger(ledgerId);
		if (ledger.getVersion() != version) {
			throw versionConflict();
		}
		ledger.changeDefaultMonthlyBudget(amount);
		if (ledger.getBudgetCycleUnit() == BudgetCycleUnit.MONTHLY) rollover.recordBaseChange(ledger);
		ledgerRepository.flush();
		return new BudgetSnapshot(
			null, null, ledger.getDefaultMonthlyBudget(), BudgetSource.DEFAULT, ledger.getVersion());
	}

	@Transactional
	public BudgetSnapshot updateMonthly(UUID userId, YearMonth yearMonth, long amount, int version) {
		UUID ledgerId = requireAdmin(userId);
		var ledger = ledgerAccessService.requireLedger(ledgerId);
		var cycle = BudgetCycle.starting(yearMonth, ledger.getBudgetCycleStartDay());
		var existing = monthlyBudgetRepository.findByLedgerIdAndBudgetMonth(ledgerId, yearMonth.atDay(1));
		if (existing.isEmpty()) {
			if (version != 0) {
				throw versionConflict();
			}
			var created = monthlyBudgetRepository.saveAndFlush(new MonthlyBudget(ledgerId, yearMonth, amount));
			return snapshot(created, cycle);
		}
		if (existing.get().getVersion() != version) {
			throw versionConflict();
		}
		existing.get().changeAmount(amount);
		monthlyBudgetRepository.flush();
		return snapshot(existing.get(), cycle);
	}

	@Transactional
	public void deleteMonthly(UUID userId, YearMonth yearMonth, int version) {
		UUID ledgerId = requireAdmin(userId);
		var budget = monthlyBudgetRepository.findByLedgerIdAndBudgetMonth(ledgerId, yearMonth.atDay(1))
			.orElseThrow(() -> new ApiException(
				HttpStatus.NOT_FOUND, "MONTHLY_BUDGET_NOT_FOUND", "해당 월의 별도 예산을 찾을 수 없습니다."));
		if (budget.getVersion() != version) {
			throw versionConflict();
		}
		monthlyBudgetRepository.delete(budget);
		monthlyBudgetRepository.flush();
	}

	@Transactional(readOnly = true)
	public long proratedBudget(UUID ledgerId, LocalDate from, LocalDate to) {
		Ledger ledger = ledgerAccessService.requireLedger(ledgerId);
		BudgetCycle opening = BudgetCycle.containing(from, ledger.getBudgetCycleUnit(),
			ledger.getBudgetCycleStartDay(), ledger.getBudgetWeekStartDay());
		if (!to.isAfter(opening.to())) {
			long applied = budgetForCycle(ledger, opening).appliedAmount();
			long days = ChronoUnit.DAYS.between(from, to) + 1;
			return BigDecimal.valueOf(applied).multiply(BigDecimal.valueOf(days))
				.divide(BigDecimal.valueOf(opening.days()), 0, RoundingMode.HALF_UP).longValueExact();
		}
		// A transfer between cycles is not new budget. Only the opening carry enters a multi-cycle range.
		long openingCarry = budgetForCycle(ledger, opening).rollover().carryAmount();
		var historicalDefaults = rollover.defaultHistory(ledger, to);
		if (ledger.getBudgetCycleUnit() == BudgetCycleUnit.WEEKLY) {
			BigDecimal total = BigDecimal.ZERO;
			for (BudgetCycle cycle = BudgetCycle.weeklyContaining(from, ledger.getBudgetWeekStartDay());
				!cycle.from().isAfter(to);
				cycle = BudgetCycle.weeklyContaining(cycle.to().plusDays(1), ledger.getBudgetWeekStartDay())) {
				LocalDate start = from.isAfter(cycle.from()) ? from : cycle.from();
				LocalDate end = to.isBefore(cycle.to()) ? to : cycle.to();
				long days = ChronoUnit.DAYS.between(start, end) + 1;
				var storedBase = historicalDefaults.floorEntry(cycle.from());
				long base = storedBase == null ? ledger.getDefaultWeeklyBudget() : storedBase.getValue();
				total = total.add(BigDecimal.valueOf(base)
					.multiply(BigDecimal.valueOf(days)).divide(BigDecimal.valueOf(7), 8, RoundingMode.HALF_UP));
			}
			return Math.addExact(total.setScale(0, RoundingMode.HALF_UP).longValueExact(), openingCarry);
		}
		int startDay = ledger.getBudgetCycleStartDay();
		BudgetCycle firstCycle = BudgetCycle.containing(from, startDay);
		BudgetCycle lastCycle = BudgetCycle.containing(to, startDay);
		var overrides = monthlyBudgetRepository.findAllByLedgerIdAndBudgetMonthBetween(
			ledgerId, firstCycle.yearMonth().atDay(1), lastCycle.yearMonth().atDay(1)).stream()
			.collect(Collectors.toMap(
				budget -> YearMonth.from(budget.getBudgetMonth()), Function.identity()));

		BigDecimal total = BigDecimal.ZERO;
		for (YearMonth month = firstCycle.yearMonth();
			!month.isAfter(lastCycle.yearMonth()); month = month.plusMonths(1)) {
			BudgetCycle cycle = BudgetCycle.starting(month, startDay);
			LocalDate segmentFrom = from.isAfter(cycle.from()) ? from : cycle.from();
			LocalDate segmentTo = to.isBefore(cycle.to()) ? to : cycle.to();
			long includedDays = ChronoUnit.DAYS.between(segmentFrom, segmentTo) + 1;
			long amount = overrides.containsKey(month)
				? overrides.get(month).getAmount()
				: historicalDefaults.floorEntry(cycle.from()) == null ? ledger.getDefaultMonthlyBudget()
					: historicalDefaults.floorEntry(cycle.from()).getValue();
			total = total.add(BigDecimal.valueOf(amount)
				.multiply(BigDecimal.valueOf(includedDays))
				.divide(BigDecimal.valueOf(cycle.days()), 8, RoundingMode.HALF_UP));
		}
		return Math.addExact(total.setScale(0, RoundingMode.HALF_UP).longValueExact(), openingCarry);
	}

	private BudgetSnapshot appliedBudget(Ledger ledger, BudgetCycle cycle) {
		return monthlyBudgetRepository.findByLedgerIdAndBudgetMonth(ledger.getId(), cycle.yearMonth().atDay(1))
			.map(budget -> snapshot(budget, cycle))
			.orElseGet(() -> new BudgetSnapshot(
				cycle.yearMonth(), cycle, ledger.getDefaultMonthlyBudget(), BudgetSource.DEFAULT, ledger.getVersion()));
	}

	private UUID ledgerId(UUID userId) {
		return ledgerAccessService.requireActiveMembership(userId).getId().getLedgerId();
	}

	private UUID requireAdmin(UUID userId) {
		var membership = ledgerAccessService.requireActiveMembership(userId);
		if (membership.getRole() != MemberRole.ADMIN) {
			throw new ApiException(HttpStatus.FORBIDDEN, "ADMIN_REQUIRED", "관리자 권한이 필요합니다.");
		}
		return membership.getId().getLedgerId();
	}

	private static BudgetSnapshot snapshot(MonthlyBudget budget, BudgetCycle cycle) {
		return new BudgetSnapshot(
			YearMonth.from(budget.getBudgetMonth()), cycle, budget.getAmount(), BudgetSource.MONTHLY_OVERRIDE,
			budget.getVersion());
	}

	private ApiException versionConflict() {
		return new ApiException(
			HttpStatus.CONFLICT, "BUDGET_VERSION_CONFLICT", "다른 사용자가 이 예산을 먼저 변경했습니다.");
	}
}
