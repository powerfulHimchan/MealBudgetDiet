package com.mealbudgetdiet.budget.domain;

import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.UUID;

import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

@Entity
@Table(name = "monthly_budgets")
public class MonthlyBudget {

	@Id
	private UUID id;

	@Column(name = "ledger_id", nullable = false)
	private UUID ledgerId;

	@Column(name = "budget_month", nullable = false)
	private LocalDate budgetMonth;

	@Column(nullable = false)
	private long amount;

	@Version
	@Column(nullable = false)
	private int version;

	@CreationTimestamp
	@Column(name = "created_at", nullable = false, updatable = false)
	private Instant createdAt;

	@UpdateTimestamp
	@Column(name = "updated_at", nullable = false)
	private Instant updatedAt;

	protected MonthlyBudget() {
	}

	public MonthlyBudget(UUID ledgerId, YearMonth yearMonth, long amount) {
		this.id = UUID.randomUUID();
		this.ledgerId = ledgerId;
		this.budgetMonth = yearMonth.atDay(1);
		this.amount = amount;
	}

	public void changeAmount(long amount) {
		this.amount = amount;
	}

	public UUID getId() {
		return id;
	}

	public UUID getLedgerId() {
		return ledgerId;
	}

	public LocalDate getBudgetMonth() {
		return budgetMonth;
	}

	public long getAmount() {
		return amount;
	}

	public int getVersion() {
		return version;
	}
}
