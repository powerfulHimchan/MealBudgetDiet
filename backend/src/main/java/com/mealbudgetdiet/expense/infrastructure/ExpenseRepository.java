package com.mealbudgetdiet.expense.infrastructure;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.mealbudgetdiet.expense.domain.Expense;

public interface ExpenseRepository extends JpaRepository<Expense, UUID> {

	@EntityGraph(attributePaths = "category")
	Optional<Expense> findByIdAndLedgerId(UUID id, UUID ledgerId);

	@EntityGraph(attributePaths = "category")
	@Query("""
		select expense from Expense expense
		where expense.ledgerId = :ledgerId
		  and (:fromDate is null or expense.spentOn >= :fromDate)
		  and (:toDate is null or expense.spentOn <= :toDate)
		  and (:categoryId is null or expense.category.id = :categoryId)
		  and (:uncategorized = false or expense.category is null)
		  and (lower(coalesce(expense.merchant, '')) like :keywordPattern
		       or lower(coalesce(expense.memo, '')) like :keywordPattern)
		  and (:cursorSpentOn is null
		       or expense.spentOn < :cursorSpentOn
		       or (expense.spentOn = :cursorSpentOn and expense.createdAt < :cursorCreatedAt)
		       or (expense.spentOn = :cursorSpentOn and expense.createdAt = :cursorCreatedAt and expense.id < :cursorId))
		order by expense.spentOn desc, expense.createdAt desc, expense.id desc
		""")
	List<Expense> search(
		@Param("ledgerId") UUID ledgerId,
		@Param("fromDate") LocalDate fromDate,
		@Param("toDate") LocalDate toDate,
		@Param("categoryId") UUID categoryId,
		@Param("uncategorized") boolean uncategorized,
		@Param("keywordPattern") String keywordPattern,
		@Param("cursorSpentOn") LocalDate cursorSpentOn,
		@Param("cursorCreatedAt") Instant cursorCreatedAt,
		@Param("cursorId") UUID cursorId,
		Pageable pageable
	);
}
