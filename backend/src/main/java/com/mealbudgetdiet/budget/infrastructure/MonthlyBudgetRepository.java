package com.mealbudgetdiet.budget.infrastructure;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.mealbudgetdiet.budget.domain.MonthlyBudget;

public interface MonthlyBudgetRepository extends JpaRepository<MonthlyBudget, UUID> {

	Optional<MonthlyBudget> findByLedgerIdAndBudgetMonth(UUID ledgerId, LocalDate budgetMonth);

	List<MonthlyBudget> findAllByLedgerIdAndBudgetMonthBetween(
		UUID ledgerId,
		LocalDate fromMonth,
		LocalDate toMonth
	);
}
