package com.mealbudgetdiet.expense.api;

import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.mealbudgetdiet.expense.application.CategorySnapshot;
import com.mealbudgetdiet.expense.application.ExpensePage;
import com.mealbudgetdiet.expense.application.ExpenseService;
import com.mealbudgetdiet.expense.application.ExpenseSnapshot;
import com.mealbudgetdiet.identity.infrastructure.MealBudgetPrincipal;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

@Validated
@RestController
@RequestMapping("/api/v1/expenses")
public class ExpenseController {

	private static final ZoneId SERVICE_ZONE = ZoneId.of("Asia/Seoul");

	private final ExpenseService expenseService;

	public ExpenseController(ExpenseService expenseService) {
		this.expenseService = expenseService;
	}

	@GetMapping
	ExpensePageResponse expenses(
		@AuthenticationPrincipal MealBudgetPrincipal principal,
		@RequestParam(required = false) LocalDate from,
		@RequestParam(required = false) LocalDate to,
		@RequestParam(required = false) UUID categoryId,
		@RequestParam(defaultValue = "false") boolean uncategorized,
		@RequestParam(required = false) @Size(max = 200) String keyword,
		@RequestParam(defaultValue = "20") @Min(1) @Max(100) int size,
		@RequestParam(required = false) String cursor
	) {
		return ExpensePageResponse.from(expenseService.search(
			principal.id(), from, to, categoryId, uncategorized, keyword, size, cursor));
	}

	@PostMapping
	ResponseEntity<ExpenseResponse> create(
		@AuthenticationPrincipal MealBudgetPrincipal principal,
		@Valid @RequestBody CreateExpenseRequest request
	) {
		var created = expenseService.create(
			principal.id(), request.amount(), request.spentOn(), request.categoryId(), request.merchant(), request.memo());
		return ResponseEntity.status(HttpStatus.CREATED).body(ExpenseResponse.from(created));
	}

	@GetMapping("/{expenseId}")
	ExpenseResponse expense(
		@AuthenticationPrincipal MealBudgetPrincipal principal,
		@PathVariable UUID expenseId
	) {
		return ExpenseResponse.from(expenseService.get(principal.id(), expenseId));
	}

	@PutMapping("/{expenseId}")
	ExpenseResponse update(
		@AuthenticationPrincipal MealBudgetPrincipal principal,
		@PathVariable UUID expenseId,
		@Valid @RequestBody UpdateExpenseRequest request
	) {
		return ExpenseResponse.from(expenseService.update(
			principal.id(),
			expenseId,
			request.amount(),
			request.spentOn(),
			request.categoryId(),
			request.merchant(),
			request.memo(),
			request.version()
		));
	}

	@DeleteMapping("/{expenseId}")
	ResponseEntity<Void> delete(
		@AuthenticationPrincipal MealBudgetPrincipal principal,
		@PathVariable UUID expenseId,
		@RequestParam @PositiveOrZero int version
	) {
		expenseService.delete(principal.id(), expenseId, version);
		return ResponseEntity.noContent().build();
	}

	public record CreateExpenseRequest(
		@NotNull(message = "금액을 입력해 주세요.")
		@Positive(message = "금액은 0보다 커야 합니다.")
		Long amount,
		@NotNull(message = "사용 날짜를 입력해 주세요.")
		LocalDate spentOn,
		@NotNull(message = "카테고리를 선택해 주세요.")
		UUID categoryId,
		@Size(max = 100, message = "상호명은 100자 이하여야 합니다.")
		String merchant,
		@Size(max = 1000, message = "메모는 1000자 이하여야 합니다.")
		String memo
	) {
	}

	public record UpdateExpenseRequest(
		@NotNull(message = "금액을 입력해 주세요.")
		@Positive(message = "금액은 0보다 커야 합니다.")
		Long amount,
		@NotNull(message = "사용 날짜를 입력해 주세요.")
		LocalDate spentOn,
		@NotNull(message = "카테고리를 선택해 주세요.")
		UUID categoryId,
		@Size(max = 100, message = "상호명은 100자 이하여야 합니다.")
		String merchant,
		@Size(max = 1000, message = "메모는 1000자 이하여야 합니다.")
		String memo,
		@NotNull(message = "버전 정보가 필요합니다.")
		@PositiveOrZero(message = "버전 정보가 올바르지 않습니다.")
		Integer version
	) {
	}

	private record ExpensePageResponse(List<ExpenseResponse> items, String nextCursor, boolean hasNext) {
		static ExpensePageResponse from(ExpensePage page) {
			return new ExpensePageResponse(
				page.items().stream().map(ExpenseResponse::from).toList(), page.nextCursor(), page.hasNext());
		}
	}

	private record ExpenseResponse(
		UUID id,
		long amount,
		LocalDate spentOn,
		CategoryResponse category,
		String merchant,
		String memo,
		int version,
		OffsetDateTime createdAt,
		OffsetDateTime updatedAt
	) {
		static ExpenseResponse from(ExpenseSnapshot snapshot) {
			return new ExpenseResponse(
				snapshot.id(), snapshot.amount(), snapshot.spentOn(), CategoryResponse.from(snapshot.category()),
				snapshot.merchant(), snapshot.memo(), snapshot.version(),
				toServiceTime(snapshot.createdAt()), toServiceTime(snapshot.updatedAt()));
		}
	}

	private record CategoryResponse(UUID id, String name) {
		static CategoryResponse from(CategorySnapshot category) {
			return category == null ? null : new CategoryResponse(category.id(), category.name());
		}
	}

	private static OffsetDateTime toServiceTime(Instant instant) {
		return instant.atZone(SERVICE_ZONE).toOffsetDateTime();
	}
}
