package com.mealbudgetdiet.expense.infrastructure;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.mealbudgetdiet.expense.domain.Category;

public interface CategoryRepository extends JpaRepository<Category, UUID> {

	List<Category> findAllByLedgerIdOrderBySortOrderAscNameAsc(UUID ledgerId);

	Optional<Category> findByIdAndLedgerId(UUID id, UUID ledgerId);
}
