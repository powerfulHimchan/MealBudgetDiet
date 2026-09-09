package com.mealbudgetdiet.expense.domain;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

@Entity
@Table(name = "expenses")
public class Expense {

	@Id
	private UUID id;

	@Column(name = "ledger_id", nullable = false)
	private UUID ledgerId;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "category_id")
	private Category category;

	@Column(nullable = false)
	private long amount;

	@Column(name = "spent_on", nullable = false)
	private LocalDate spentOn;

	@Column(length = 100)
	private String merchant;

	@Column(columnDefinition = "text")
	private String memo;

	@Version
	@Column(nullable = false)
	private int version;

	@CreationTimestamp
	@Column(name = "created_at", nullable = false, updatable = false)
	private Instant createdAt;

	@UpdateTimestamp
	@Column(name = "updated_at", nullable = false)
	private Instant updatedAt;

	protected Expense() {
	}

	public Expense(
		UUID ledgerId,
		Category category,
		long amount,
		LocalDate spentOn,
		String merchant,
		String memo
	) {
		this.id = UUID.randomUUID();
		this.ledgerId = ledgerId;
		update(category, amount, spentOn, merchant, memo);
	}

	public void update(Category category, long amount, LocalDate spentOn, String merchant, String memo) {
		this.category = category;
		this.amount = amount;
		this.spentOn = spentOn;
		this.merchant = normalizeOptional(merchant);
		this.memo = normalizeOptional(memo);
	}

	private static String normalizeOptional(String value) {
		return value == null || value.isBlank() ? null : value.strip();
	}

	public UUID getId() {
		return id;
	}

	public UUID getLedgerId() {
		return ledgerId;
	}

	public Category getCategory() {
		return category;
	}

	public long getAmount() {
		return amount;
	}

	public LocalDate getSpentOn() {
		return spentOn;
	}

	public String getMerchant() {
		return merchant;
	}

	public String getMemo() {
		return memo;
	}

	public int getVersion() {
		return version;
	}

	public Instant getCreatedAt() {
		return createdAt;
	}

	public Instant getUpdatedAt() {
		return updatedAt;
	}
}
