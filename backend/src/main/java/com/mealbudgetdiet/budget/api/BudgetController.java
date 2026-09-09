package com.mealbudgetdiet.budget.api;

import java.time.YearMonth;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.mealbudgetdiet.budget.application.BudgetService;
import com.mealbudgetdiet.budget.application.BudgetSnapshot;
import com.mealbudgetdiet.budget.application.BudgetSource;
import com.mealbudgetdiet.identity.infrastructure.MealBudgetPrincipal;
import com.mealbudgetdiet.shared.api.ApiException;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;

@Validated
@RestController
@RequestMapping("/api/v1/budgets")
public class BudgetController {

	private final BudgetService budgetService;

	public BudgetController(BudgetService budgetService) {
		this.budgetService = budgetService;
	}

	@GetMapping("/{yearMonth}")
	BudgetResponse budget(
		@AuthenticationPrincipal MealBudgetPrincipal principal,
		@PathVariable String yearMonth
	) {
		return BudgetResponse.from(budgetService.getAppliedBudget(principal.id(), parseYearMonth(yearMonth)));
	}

	@PutMapping("/default")
	BudgetResponse updateDefault(
		@AuthenticationPrincipal MealBudgetPrincipal principal,
		@Valid @RequestBody BudgetRequest request
	) {
		return BudgetResponse.from(budgetService.updateDefault(principal.id(), request.amount(), request.version()));
	}

	@PutMapping("/{yearMonth}")
	BudgetResponse updateMonthly(
		@AuthenticationPrincipal MealBudgetPrincipal principal,
		@PathVariable String yearMonth,
		@Valid @RequestBody BudgetRequest request
	) {
		return BudgetResponse.from(budgetService.updateMonthly(
			principal.id(), parseYearMonth(yearMonth), request.amount(), request.version()));
	}

	@DeleteMapping("/{yearMonth}")
	ResponseEntity<Void> deleteMonthly(
		@AuthenticationPrincipal MealBudgetPrincipal principal,
		@PathVariable String yearMonth,
		@RequestParam @PositiveOrZero int version
	) {
		budgetService.deleteMonthly(principal.id(), parseYearMonth(yearMonth), version);
		return ResponseEntity.noContent().build();
	}

	public record BudgetRequest(
		@NotNull(message = "예산 금액을 입력해 주세요.")
		@Positive(message = "예산은 0보다 커야 합니다.")
		Long amount,
		@NotNull(message = "버전 정보가 필요합니다.")
		@PositiveOrZero(message = "버전 정보가 올바르지 않습니다.")
		Integer version
	) {
	}

	private record BudgetResponse(String yearMonth, long amount, BudgetSource source, int version) {
		static BudgetResponse from(BudgetSnapshot snapshot) {
			return new BudgetResponse(
				snapshot.yearMonth() == null ? null : snapshot.yearMonth().toString(),
				snapshot.amount(), snapshot.source(), snapshot.version());
		}
	}

	private static YearMonth parseYearMonth(String value) {
		try {
			return YearMonth.parse(value);
		} catch (java.time.format.DateTimeParseException exception) {
			throw new ApiException(
				org.springframework.http.HttpStatus.BAD_REQUEST,
				"INVALID_YEAR_MONTH",
				"월은 YYYY-MM 형식이어야 합니다."
			);
		}
	}
}
