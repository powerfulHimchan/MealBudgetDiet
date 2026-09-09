package com.mealbudgetdiet.media.infrastructure;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.mealbudgetdiet.media.domain.ExpenseImage;
import com.mealbudgetdiet.media.domain.ExpenseImageId;

public interface ExpenseImageRepository extends JpaRepository<ExpenseImage, ExpenseImageId> {
	List<ExpenseImage> findAllByExpenseIdOrderBySortOrder(UUID expenseId);
}
