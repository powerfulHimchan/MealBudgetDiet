package com.mealbudgetdiet.analytics.application;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.ArgumentPreparedStatementSetter;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.mealbudgetdiet.analytics.application.DashboardSnapshot.RecentExpense;
import com.mealbudgetdiet.analytics.application.StatisticsSnapshot.CategoryAmount;
import com.mealbudgetdiet.analytics.application.StatisticsSnapshot.Comparison;
import com.mealbudgetdiet.analytics.application.StatisticsSnapshot.DailyAmount;
import com.mealbudgetdiet.budget.application.BudgetService;
import com.mealbudgetdiet.budget.domain.BudgetCycle;
import com.mealbudgetdiet.expense.infrastructure.CategoryRepository;
import com.mealbudgetdiet.ledger.application.LedgerAccessService;
import com.mealbudgetdiet.ledger.domain.Ledger;
import com.mealbudgetdiet.shared.api.ApiException;

@Service
public class AnalyticsService {

	private static final ZoneId SERVICE_ZONE = ZoneId.of("Asia/Seoul");

	private final LedgerAccessService ledgerAccessService;
	private final BudgetService budgetService;
	private final CategoryRepository categoryRepository;
	private final JdbcTemplate jdbcTemplate;
	private final Clock clock;

	public AnalyticsService(
		LedgerAccessService ledgerAccessService,
		BudgetService budgetService,
		CategoryRepository categoryRepository,
		JdbcTemplate jdbcTemplate,
		Clock clock
	) {
		this.ledgerAccessService = ledgerAccessService;
		this.budgetService = budgetService;
		this.categoryRepository = categoryRepository;
		this.jdbcTemplate = jdbcTemplate;
		this.clock = clock;
	}

	@Transactional(readOnly = true)
	public DashboardSnapshot dashboard(UUID userId, YearMonth requestedMonth, int recentSize) {
		Ledger ledger = ledger(userId);
		LocalDate today = LocalDate.now(clock.withZone(SERVICE_ZONE));
		BudgetCycle cycle = requestedMonth == null
			? BudgetCycle.containing(today, ledger.getBudgetCycleStartDay())
			: BudgetCycle.starting(requestedMonth, ledger.getBudgetCycleStartDay());
		UUID ledgerId = ledger.getId();
		var budget = budgetService.getAppliedBudget(userId, cycle.yearMonth());
		LocalDate from = cycle.from();
		LocalDate to = cycle.to();
		long spent = totalAmount(ledgerId, from, to);
		long projectedSpent = projectedAmount(spent, from, to, today);
		BigDecimal usageRate = percentage(spent, budget.amount());
		DashboardStatus status = usageRate.compareTo(BigDecimal.valueOf(100)) >= 0
			? DashboardStatus.EXCEEDED
			: usageRate.compareTo(BigDecimal.valueOf(ledger.getPushUsageThreshold())) >= 0
				? DashboardStatus.WARNING
				: DashboardStatus.NORMAL;

		List<RecentExpense> recent = jdbcTemplate.query("""
			select e.id, e.amount, e.spent_on, coalesce(c.name, '분류 없음') category_name,
			       e.merchant, e.version
			from expenses e
			left join categories c on c.id = e.category_id
			where e.ledger_id = ? and e.spent_on between ? and ?
			order by e.spent_on desc, e.created_at desc, e.id desc
			limit ?
			""", (resultSet, rowNumber) -> new RecentExpense(
				resultSet.getObject("id", UUID.class),
				resultSet.getLong("amount"),
				resultSet.getObject("spent_on", LocalDate.class),
				resultSet.getString("category_name"),
				resultSet.getString("merchant"),
				resultSet.getInt("version")
			), ledgerId, from, to, recentSize);

		return new DashboardSnapshot(
			cycle.yearMonth(), new DashboardSnapshot.Period(from, to), budget.amount(), spent,
			budget.amount() - spent, projectedSpent, usageRate, ledger.getPushUsageThreshold(), status, recent);
	}

	@Transactional(readOnly = true)
	public StatisticsSnapshot statistics(
		UUID userId,
		YearMonth requestedCycle,
		LocalDate requestedFrom,
		LocalDate requestedTo
	) {
		Ledger ledger = ledger(userId);
		LocalDate from;
		LocalDate to;
		if (requestedCycle != null) {
			if (requestedFrom != null || requestedTo != null) {
				throw new ApiException(
					HttpStatus.BAD_REQUEST, "STATISTICS_PERIOD_CONFLICT",
					"예산 주기와 직접 지정 기간은 함께 조회할 수 없습니다.");
			}
			BudgetCycle cycle = BudgetCycle.starting(requestedCycle, ledger.getBudgetCycleStartDay());
			from = cycle.from();
			to = cycle.to();
		} else {
			validateRange(requestedFrom, requestedTo);
			from = requestedFrom;
			to = requestedTo;
		}
		UUID ledgerId = ledger.getId();
		long total = totalAmount(ledgerId, from, to);
		long budget = budgetService.proratedBudget(ledgerId, from, to);
		var comparisonPeriod = comparisonPeriod(ledger, from, to);
		long previousTotal = totalAmount(ledgerId, comparisonPeriod.from(), comparisonPeriod.to());
		BigDecimal changeRate = previousTotal == 0
			? null
			: percentage(total - previousTotal, previousTotal);

		List<DailyAmount> daily = jdbcTemplate.query("""
			select spent_on, sum(amount) total_amount
			from expenses
			where ledger_id = ? and spent_on between ? and ?
			group by spent_on
			order by spent_on
			""", (resultSet, rowNumber) -> new DailyAmount(
				resultSet.getObject("spent_on", LocalDate.class), resultSet.getLong("total_amount")),
			ledgerId, from, to);

		List<CategoryAmount> categories = jdbcTemplate.query("""
			select e.category_id, coalesce(c.name, '분류 없음') category_name, sum(e.amount) total_amount
			from expenses e
			left join categories c on c.id = e.category_id
			where e.ledger_id = ? and e.spent_on between ? and ?
			group by e.category_id, c.name
			order by total_amount desc, category_name
			""", (resultSet, rowNumber) -> {
				UUID categoryId = resultSet.getObject("category_id", UUID.class);
				long amount = resultSet.getLong("total_amount");
				return new CategoryAmount(
					categoryId, resultSet.getString("category_name"), amount,
					total == 0 ? BigDecimal.ZERO.setScale(2) : percentage(amount, total));
			}, ledgerId, from, to);

		return new StatisticsSnapshot(
			new StatisticsSnapshot.Period(from, to),
			total,
			new StatisticsSnapshot.Budget(budget, percentage(total, budget)),
			new Comparison(
				comparisonPeriod.from(), comparisonPeriod.to(), previousTotal,
				total - previousTotal, changeRate),
			daily,
			categories
		);
	}

	@Transactional(readOnly = true)
	public CsvExport exportCsv(
		UUID userId,
		LocalDate from,
		LocalDate to,
		UUID categoryId,
		String keyword
	) {
		validateRange(from, to);
		UUID ledgerId = ledger(userId).getId();
		if (categoryId != null && categoryRepository.findByIdAndLedgerId(categoryId, ledgerId).isEmpty()) {
			throw new ApiException(HttpStatus.BAD_REQUEST, "CATEGORY_INVALID", "현재 장부에서 사용할 수 있는 카테고리입니다.");
		}

		StringBuilder sql = new StringBuilder("""
			select e.spent_on, e.amount, coalesce(c.name, '분류 없음') category_name, e.merchant, e.memo
			from expenses e
			left join categories c on c.id = e.category_id
			where e.ledger_id = ? and e.spent_on between ? and ?
			""");
		List<Object> arguments = new ArrayList<>(List.of(ledgerId, from, to));
		if (categoryId != null) {
			sql.append(" and e.category_id = ?");
			arguments.add(categoryId);
		}
		if (keyword != null && !keyword.isBlank()) {
			sql.append(" and (lower(coalesce(e.merchant, '')) like ? or lower(coalesce(e.memo, '')) like ?)");
			String pattern = "%" + keyword.strip().toLowerCase(Locale.ROOT) + "%";
			arguments.add(pattern);
			arguments.add(pattern);
		}
		sql.append(" order by e.spent_on desc, e.created_at desc, e.id desc");

		var rows = jdbcTemplate.query(
			sql.toString(),
			new ArgumentPreparedStatementSetter(arguments.toArray()),
			(resultSet, rowNumber) -> List.of(
				resultSet.getObject("spent_on", LocalDate.class).toString(),
				Long.toString(resultSet.getLong("amount")),
				resultSet.getString("category_name"),
				java.util.Objects.toString(resultSet.getString("merchant"), ""),
				java.util.Objects.toString(resultSet.getString("memo"), "")
			)
		);

		StringBuilder csv = new StringBuilder("﻿사용일,금액,카테고리,상호명,메모
");
		for (List<String> row : rows) {
			csv.append(row.stream().map(AnalyticsService::csvCell).collect(java.util.stream.Collectors.joining(",")))
				.append("
");
		}
		return new CsvExport(
			"meal-expenses-%s_%s.csv".formatted(from, to),
			csv.toString().getBytes(StandardCharsets.UTF_8));
	}

	private long totalAmount(UUID ledgerId, LocalDate from, LocalDate to) {
		Long total = jdbcTemplate.queryForObject(
			"select coalesce(sum(amount), 0) from expenses where ledger_id = ? and spent_on between ? and ?",
			Long.class, ledgerId, from, to);
		return total == null ? 0 : total;
	}

	private long projectedAmount(long spent, LocalDate from, LocalDate to, LocalDate today) {
		if (spent == 0 || today.isBefore(from)) {
			return 0;
		}
		long totalDays = ChronoUnit.DAYS.between(from, to) + 1;
		long elapsedDays = today.isAfter(to)
			? totalDays
			: ChronoUnit.DAYS.between(from, today) + 1;
		return BigDecimal.valueOf(spent)
			.multiply(BigDecimal.valueOf(totalDays))
			.divide(BigDecimal.valueOf(elapsedDays), 0, RoundingMode.HALF_UP)
			.longValueExact();
	}

	private Ledger ledger(UUID userId) {
		UUID ledgerId = ledgerAccessService.requireActiveMembership(userId).getId().getLedgerId();
		return ledgerAccessService.requireLedger(ledgerId);
	}

	private void validateRange(LocalDate from, LocalDate to) {
		if (from == null || to == null) {
			throw new ApiException(HttpStatus.BAD_REQUEST, "DATE_RANGE_REQUIRED", "시작일과 종료일을 입력해 주세요.");
		}
		if (from.isAfter(to)) {
			throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_DATE_RANGE", "시작일은 종료일보다 늦을 수 없습니다.");
		}
	}

	private StatisticsSnapshot.Period comparisonPeriod(Ledger ledger, LocalDate from, LocalDate to) {
		int startDay = ledger.getBudgetCycleStartDay();
		BudgetCycle firstCycle = BudgetCycle.containing(from, startDay);
		BudgetCycle lastCycle = BudgetCycle.containing(to, startDay);
		long cycleCount = ChronoUnit.MONTHS.between(firstCycle.yearMonth(), lastCycle.yearMonth()) + 1;
		boolean wholeBudgetCycles = from.equals(firstCycle.from()) && to.equals(lastCycle.to());
		if (wholeBudgetCycles && (cycleCount == 1 || cycleCount == 3)) {
			BudgetCycle previousStart = BudgetCycle.starting(
				firstCycle.yearMonth().minusMonths(cycleCount), startDay);
			BudgetCycle previousEnd = BudgetCycle.starting(firstCycle.yearMonth().minusMonths(1), startDay);
			return new StatisticsSnapshot.Period(previousStart.from(), previousEnd.to());
		}
		YearMonth fromMonth = YearMonth.from(from);
		YearMonth toMonth = YearMonth.from(to);
		boolean wholeCalendarMonths = from.equals(fromMonth.atDay(1)) && to.equals(toMonth.atEndOfMonth());
		long monthCount = ChronoUnit.MONTHS.between(fromMonth, toMonth) + 1;
		if (wholeCalendarMonths && (monthCount == 1 || monthCount == 3)) {
			YearMonth previousStart = fromMonth.minusMonths(monthCount);
			YearMonth previousEnd = fromMonth.minusMonths(1);
			return new StatisticsSnapshot.Period(previousStart.atDay(1), previousEnd.atEndOfMonth());
		}
		if (from.getMonthValue() == 1 && from.getDayOfMonth() == 1
			&& to.getMonthValue() == 12 && to.getDayOfMonth() == 31
			&& from.getYear() == to.getYear()) {
			return new StatisticsSnapshot.Period(from.minusYears(1), to.minusYears(1));
		}
		long days = ChronoUnit.DAYS.between(from, to) + 1;
		return new StatisticsSnapshot.Period(from.minusDays(days), from.minusDays(1));
	}

	private static BigDecimal percentage(long numerator, long denominator) {
		return BigDecimal.valueOf(numerator)
			.multiply(BigDecimal.valueOf(100))
			.divide(BigDecimal.valueOf(denominator), 2, RoundingMode.HALF_UP);
	}

	private static String csvCell(String rawValue) {
		String value = rawValue == null ? "" : rawValue;
		if (!value.isEmpty() && "=+-@".indexOf(value.charAt(0)) >= 0) {
			value = "'" + value;
		}
		return """ + value.replace(""", """") + """;
	}
}
