package com.mealbudgetdiet.ledger.domain;

import java.time.Instant;
import java.util.UUID;

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
		this.status = LedgerStatus.ACTIVE;
	}

	public UUID getId() {
		return id;
	}
}
