package com.mealbudgetdiet.expense.domain;

import java.time.Instant;
import java.util.UUID;

import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

@Entity
@Table(name = "categories")
public class Category {

	@Id
	private UUID id;

	@Column(name = "ledger_id", nullable = false)
	private UUID ledgerId;

	@Column(nullable = false, length = 50)
	private String name;

	@Column(name = "sort_order", nullable = false)
	private int sortOrder;

	@Version
	@Column(nullable = false)
	private int version;

	@CreationTimestamp
	@Column(name = "created_at", nullable = false, updatable = false)
	private Instant createdAt;

	@UpdateTimestamp
	@Column(name = "updated_at", nullable = false)
	private Instant updatedAt;

	protected Category() {
	}

	public Category(UUID ledgerId, String name, int sortOrder) {
		this.id = UUID.randomUUID();
		this.ledgerId = ledgerId;
		this.name = name.strip();
		this.sortOrder = sortOrder;
	}

	public void update(String name, int sortOrder) {
		this.name = name.strip();
		this.sortOrder = sortOrder;
	}

	public UUID getId() {
		return id;
	}

	public UUID getLedgerId() {
		return ledgerId;
	}

	public String getName() {
		return name;
	}

	public int getSortOrder() {
		return sortOrder;
	}

	public int getVersion() {
		return version;
	}
}
