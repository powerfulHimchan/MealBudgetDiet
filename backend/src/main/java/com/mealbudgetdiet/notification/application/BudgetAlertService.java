package com.mealbudgetdiet.notification.application;

import java.math.BigInteger;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.mealbudgetdiet.budget.application.BudgetService;
import com.mealbudgetdiet.budget.domain.BudgetCycle;
import com.mealbudgetdiet.expense.domain.Expense;
import com.mealbudgetdiet.ledger.application.LedgerAccessService;
import com.mealbudgetdiet.notification.domain.BudgetAlertType;
import com.mealbudgetdiet.notification.infrastructure.PushSubscriptionRepository;

@Service
public class BudgetAlertService {

	private static final ZoneId SERVICE_ZONE = ZoneId.of("Asia/Seoul");
	private static final BudgetAlertType ALERT_TYPE = BudgetAlertType.MONTHLY_BUDGET_OVERRUN_RISK;

	private final BudgetService budgetService;
	private final PushSubscriptionRepository subscriptionRepository;
	private final LedgerAccessService ledgerAccessService;
	private final JdbcTemplate jdbcTemplate;
	private final Clock clock;

	public BudgetAlertService(
		BudgetService budgetService,
		PushSubscriptionRepository subscriptionRepository,
		LedgerAccessService ledgerAccessService,
		JdbcTemplate jdbcTemplate,
		Clock clock
	) {
		this.budgetService = budgetService;
		this.subscriptionRepository = subscriptionRepository;
		this.ledgerAccessService = ledgerAccessService;
		this.jdbcTemplate = jdbcTemplate;
		this.clock = clock;
	}

	@Transactional
	public void evaluateNewExpense(UUID userId, Expense expense) {
		LocalDate today = LocalDate.now(clock.withZone(SERVICE_ZONE));
		var ledger = ledgerAccessService.requireLedger(expense.getLedgerId());
		BudgetCycle cycle = BudgetCycle.containing(today, ledger.getBudgetCycleStartDay());
		if (!cycle.contains(expense.getSpentOn())) {
			return;
		}

		long monthlyBudget = budgetService.getAppliedBudget(userId, cycle.yearMonth()).amount();
		long totalSpent = totalSpent(expense.getLedgerId(), cycle);
		int cycleDays = cycle.days();
		int elapsedDays = Math.toIntExact(ChronoUnit.DAYS.between(cycle.from(), today) + 1);
		int remainingDays = Math.toIntExact(ChronoUnit.DAYS.between(today, cycle.to()) + 1);

		if (!matchesAlertCondition(monthlyBudget, totalSpent, elapsedDays, cycleDays)) {
			return;
		}

		UUID alertId = UUID.randomUUID();
		int inserted = jdbcTemplate.update("""
			insert into budget_alerts (
			  id, ledger_id, triggered_by_expense_id, alert_month, alert_type,
			  monthly_budget, total_spent, remaining_days, cycle_days
			) values (?, ?, ?, ?, ?, ?, ?, ?, ?)
			on conflict (ledger_id, alert_month, alert_type) do nothing
			""", alertId, expense.getLedgerId(), expense.getId(), cycle.yearMonth().atDay(1), ALERT_TYPE.name(),
			monthlyBudget, totalSpent, remainingDays, cycleDays);
		if (inserted == 0) {
			return;
		}

		for (var subscription : subscriptionRepository.findAllActiveForLedger(expense.getLedgerId())) {
			jdbcTemplate.update("""
				insert into push_deliveries (
				  id, budget_alert_id, push_subscription_id, status, attempt_count, next_attempt_at
				) values (?, ?, ?, 'PENDING', 0, current_timestamp)
				on conflict (budget_alert_id, push_subscription_id) do nothing
				""", UUID.randomUUID(), alertId, subscription.getId());
		}
	}

	static boolean matchesAlertCondition(
		long monthlyBudget,
		long totalSpent,
		int elapsedDays,
		int cycleDays
	) {
		if (monthlyBudget <= 0 || totalSpent < 0 || elapsedDays <= 0 || elapsedDays > cycleDays) {
			return false;
		}
		BigInteger budget = BigInteger.valueOf(monthlyBudget);
		BigInteger spent = BigInteger.valueOf(totalSpent);
		boolean usageAtLeastEighty = spent.multiply(BigInteger.valueOf(100))
			.compareTo(budget.multiply(BigInteger.valueOf(80))) >= 0;
		boolean projectedSpendOverBudget = spent.multiply(BigInteger.valueOf(cycleDays))
			.compareTo(budget.multiply(BigInteger.valueOf(elapsedDays))) > 0;
		return usageAtLeastEighty && projectedSpendOverBudget;
	}

	private long totalSpent(UUID ledgerId, BudgetCycle cycle) {
		Long total = jdbcTemplate.queryForObject(
			"select coalesce(sum(amount), 0) from expenses where ledger_id = ? and spent_on between ? and ?",
			Long.class, ledgerId, cycle.from(), cycle.to());
		return total == null ? 0 : total;
	}
}
