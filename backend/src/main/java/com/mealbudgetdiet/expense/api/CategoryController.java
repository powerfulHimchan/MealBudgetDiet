package com.mealbudgetdiet.expense.api;

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

import com.mealbudgetdiet.expense.application.CategoryService;
import com.mealbudgetdiet.expense.application.CategorySnapshot;
import com.mealbudgetdiet.identity.infrastructure.MealBudgetPrincipal;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

@Validated
@RestController
@RequestMapping("/api/v1/categories")
public class CategoryController {

	private final CategoryService categoryService;

	public CategoryController(CategoryService categoryService) {
		this.categoryService = categoryService;
	}

	@GetMapping
	CategoriesResponse categories(@AuthenticationPrincipal MealBudgetPrincipal principal) {
		return new CategoriesResponse(categoryService.getCategories(principal.id()).stream()
			.map(CategoryResponse::from)
			.toList());
	}

	@PostMapping
	ResponseEntity<CategoryResponse> create(
		@AuthenticationPrincipal MealBudgetPrincipal principal,
		@Valid @RequestBody CreateCategoryRequest request
	) {
		var created = categoryService.create(principal.id(), request.name(), request.sortOrder());
		return ResponseEntity.status(HttpStatus.CREATED).body(CategoryResponse.from(created));
	}

	@PutMapping("/{categoryId}")
	CategoryResponse update(
		@AuthenticationPrincipal MealBudgetPrincipal principal,
		@PathVariable UUID categoryId,
		@Valid @RequestBody UpdateCategoryRequest request
	) {
		return CategoryResponse.from(categoryService.update(
			principal.id(), categoryId, request.name(), request.sortOrder(), request.version()));
	}

	@DeleteMapping("/{categoryId}")
	ResponseEntity<Void> delete(
		@AuthenticationPrincipal MealBudgetPrincipal principal,
		@PathVariable UUID categoryId,
		@RequestParam @PositiveOrZero int version
	) {
		categoryService.delete(principal.id(), categoryId, version);
		return ResponseEntity.noContent().build();
	}

	private record CategoriesResponse(List<CategoryResponse> items) {
	}

	public record CategoryResponse(UUID id, String name, int sortOrder, int version) {
		static CategoryResponse from(CategorySnapshot snapshot) {
			return new CategoryResponse(snapshot.id(), snapshot.name(), snapshot.sortOrder(), snapshot.version());
		}
	}

	public record CreateCategoryRequest(
		@NotBlank(message = "카테고리 이름을 입력해 주세요.")
		@Size(max = 50, message = "카테고리 이름은 50자 이하여야 합니다.")
		String name,
		@NotNull(message = "정렬 순서를 입력해 주세요.")
		@Min(value = 1, message = "정렬 순서는 1 이상이어야 합니다.")
		Integer sortOrder
	) {
	}

	public record UpdateCategoryRequest(
		@NotBlank(message = "카테고리 이름을 입력해 주세요.")
		@Size(max = 50, message = "카테고리 이름은 50자 이하여야 합니다.")
		String name,
		@NotNull(message = "정렬 순서를 입력해 주세요.")
		@Min(value = 1, message = "정렬 순서는 1 이상이어야 합니다.")
		Integer sortOrder,
		@NotNull(message = "버전 정보가 필요합니다.")
		@PositiveOrZero(message = "버전 정보가 올바르지 않습니다.")
		Integer version
	) {
	}
}
