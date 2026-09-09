package com.mealbudgetdiet.media.domain;

import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;

@Entity
@IdClass(ExpenseImageId.class)
@Table(name = "expense_images")
public class ExpenseImage {

	@Id
	@Column(name = "expense_id", nullable = false)
	private UUID expenseId;

	@Id
	@Column(name = "image_id", nullable = false)
	private UUID imageId;

	@Column(name = "sort_order", nullable = false)
	private int sortOrder;

	protected ExpenseImage() {
	}

	public ExpenseImage(UUID expenseId, UUID imageId, int sortOrder) {
		this.expenseId = expenseId;
		this.imageId = imageId;
		this.sortOrder = sortOrder;
	}

	public UUID getExpenseId() { return expenseId; }
	public UUID getImageId() { return imageId; }
	public int getSortOrder() { return sortOrder; }
}
