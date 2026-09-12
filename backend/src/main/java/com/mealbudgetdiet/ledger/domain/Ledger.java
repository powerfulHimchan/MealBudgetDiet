package com.mealbudgetdiet.ledger.domain;

import java.time.Instant;
import java.util.UUID;

import com.mealbudgetdiet.budget.domain.BudgetCycleUnit;

import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

@Entity
@Table(name = "ledgers")
public class Ledger {

	@Id
	private UUID id;

	@Column(nullable = false, length = 100)
	private String name;

	@Column(name = "default_monthly_budget", nullable = false)
	private long defaultMonthlyBudget;

	@Column(name = "budget_cycle_start_day", nullable = false)
	private int budgetCycleStartDay;

	@Enumerated(EnumType.STRING)
	@Column(name = "budget_cycle_unit", nullable = false, length = 20)
	private BudgetCycleUnit budgetCycleUnit;

	@Column(name = "budget_week_start_day", nullable = false)
	private int budgetWeekStartDay;

	@Column(name = "default_weekly_budget")
	private Long defaultWeeklyBudget;

	@Column(name = "push_usage_threshold", nullable = false)
	private int pushUsageThreshold;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 20)
	private LedgerStatus status;

	@Version
	@Column(nullable = false)
	private int version;

	@CreationTimestamp
	@Column(name = "created_at", nullable = false, updatable = false)
	private Instant createdAt;

	@UpdateTimestamp
	@Column(name = "updated_at", nullable = false)
	private Instant updatedAt;

	protected Ledger() {
	}

	public Ledger(String name, long defaultMonthlyBudget) {
		this.id = UUID.randomUUID();
		this.name = name;
		this.defaultMonthlyBudget = defaultMonthlyBudget;
		this.budgetCycleStartDay = 1;
		this.budgetCycleUnit = BudgetCycleUnit.MONTHLY;
		this.budgetWeekStartDay = 1;
		this.pushUsageThreshold = 80;
		this.status = LedgerStatus.ACTIVE;
	}

	public UUID getId() {
		return id;
	}

	public String getName() {
		return name;
	}

	public long getDefaultMonthlyBudget() {
		return defaultMonthlyBudget;
	}

	public int getBudgetCycleStartDay() {
		return budgetCycleStartDay;
	}

	public BudgetCycleUnit getBudgetCycleUnit() {
		return budgetCycleUnit;
	}

	public int getBudgetWeekStartDay() {
		return budgetWeekStartDay;
	}

	public Long getDefaultWeeklyBudget() {
		return defaultWeeklyBudget;
	}

	public int getPushUsageThreshold() {
		return pushUsageThreshold;
	}

	public LedgerStatus getStatus() {
		return status;
	}

	public int getVersion() {
		return version;
	}

	public void changeDefaultMonthlyBudget(long defaultMonthlyBudget) {
		this.defaultMonthlyBudget = defaultMonthlyBudget;
	}

	public void changeBudgetCycleStartDay(int budgetCycleStartDay) {
		this.budgetCycleStartDay = budgetCycleStartDay;
	}

	public void changeBudgetCycle(BudgetCycleUnit unit, int monthStartDay, int weekStartDay, Long weeklyBudget) {
		if (unit == null || monthStartDay < 1 || monthStartDay > 31 || weekStartDay < 1 || weekStartDay > 7
			|| (weeklyBudget != null && weeklyBudget <= 0)
			|| (unit == BudgetCycleUnit.WEEKLY && weeklyBudget == null)) {
			throw new IllegalArgumentException("예산 주기 설정이 올바르지 않습니다.");
		}
		this.budgetCycleUnit = unit;
		this.budgetCycleStartDay = monthStartDay;
		this.budgetWeekStartDay = weekStartDay;
		this.defaultWeeklyBudget = weeklyBudget;
	}

	public void changePushUsageThreshold(int pushUsageThreshold) {
		this.pushUsageThreshold = pushUsageThreshold;
	}
}
