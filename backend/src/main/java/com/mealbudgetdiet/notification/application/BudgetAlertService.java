package com.mealbudgetdiet.notification.application;

import java.math.BigInteger;
import java.time.Clock;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.mealbudgetdiet.budget.application.BudgetService;
import com.mealbudgetdiet.expense.domain.Expense;
import com.mealbudgetdiet.notification.infrastructure.PushSubscriptionRepository;

@Service
public class BudgetAlertService {

	private static final ZoneId SERVICE_ZONE = ZoneId.of("Asia/Seoul");
	private static final String ALERT_TYPE = "MONTHLY_BUDGET_SURPLUS";

	private final BudgetService budgetService;
	private final PushSubscriptionRepository subscriptionRepository;
	private final JdbcTemplate jdbcTemplate;
	private final Clock clock;

	public BudgetAlertService(
		BudgetService budgetService,
		PushSubscriptionRepository subscriptionRepository,
		JdbcTemplate jdbcTemplate,
		Clock clock
	) {
		this.budgetService = budgetService;
		this.subscriptionRepository = subscriptionRepository;
		this.jdbcTemplate = jdbcTemplate;
		this.clock = clock;
	}

	@Transactional
	public void evaluateNewExpense(UUID userId, Expense expense) {
		LocalDate today = LocalDate.now(clock.withZone(SERVICE_ZONE));
		YearMonth currentMonth = YearMonth.from(today);
		if (!YearMonth.from(expense.getSpentOn()).equals(currentMonth)) {
			return;
		}

		long monthlyBudget = budgetService.getAppliedBudget(userId, currentMonth).amount();
		long totalSpent = totalSpent(expense.getLedgerId(), currentMonth);
		int remainingDays = (int) ChronoUnit.DAYS.between(today, currentMonth.atEndOfMonth()) + 1;
		long remainingBudget = monthlyBudget - totalSpent;

		if (!matchesAlertCondition(monthlyBudget, totalSpent, remainingBudget, remainingDays, currentMonth.lengthOfMonth())) {
			return;
		}

		UUID alertId = UUID.randomUUID();
		int inserted = jdbcTemplate.update("""
			insert into budget_alerts (
			  id, ledger_id, triggered_by_expense_id, alert_month, alert_type,
			  monthly_budget, total_spent, remaining_days
			) values (?, ?, ?, ?, ?, ?, ?, ?)
			on conflict (ledger_id, alert_month, alert_type) do nothing
			""", alertId, expense.getLedgerId(), expense.getId(), currentMonth.atDay(1), ALERT_TYPE,
			monthlyBudget, totalSpent, remainingDays);
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
		long remainingBudget,
		int remainingDays,
		int daysInMonth
	) {
		if (monthlyBudget <= 0 || remainingDays <= 0 || remainingBudget <= 0) {
			return false;
		}
		BigInteger budget = BigInteger.valueOf(monthlyBudget);
		boolean usageAtLeastEighty = BigInteger.valueOf(totalSpent).multiply(BigInteger.valueOf(100))
			.compareTo(budget.multiply(BigInteger.valueOf(80))) >= 0;
		boolean dailyAllowanceOverDouble = BigInteger.valueOf(remainingBudget).multiply(BigInteger.valueOf(daysInMonth))
			.compareTo(budget.multiply(BigInteger.valueOf(remainingDays)).multiply(BigInteger.TWO)) > 0;
		return usageAtLeastEighty && dailyAllowanceOverDouble;
	}

	private long totalSpent(UUID ledgerId, YearMonth yearMonth) {
		Long total = jdbcTemplate.queryForObject(
			"select coalesce(sum(amount), 0) from expenses where ledger_id = ? and spent_on between ? and ?",
			Long.class, ledgerId, yearMonth.atDay(1), yearMonth.atEndOfMonth());
		return total == null ? 0 : total;
	}
}
