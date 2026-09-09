package com.mealbudgetdiet.expense.application;

import java.util.List;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.mealbudgetdiet.expense.domain.Category;
import com.mealbudgetdiet.expense.infrastructure.CategoryRepository;
import com.mealbudgetdiet.ledger.application.LedgerAccessService;
import com.mealbudgetdiet.ledger.domain.MemberRole;
import com.mealbudgetdiet.shared.api.ApiException;

@Service
public class CategoryService {

	private final LedgerAccessService ledgerAccessService;
	private final CategoryRepository categoryRepository;

	public CategoryService(LedgerAccessService ledgerAccessService, CategoryRepository categoryRepository) {
		this.ledgerAccessService = ledgerAccessService;
		this.categoryRepository = categoryRepository;
	}

	@Transactional(readOnly = true)
	public List<CategorySnapshot> getCategories(UUID userId) {
		UUID ledgerId = ledgerAccessService.requireActiveMembership(userId).getId().getLedgerId();
		return categoryRepository.findAllByLedgerIdOrderBySortOrderAscNameAsc(ledgerId).stream()
			.map(CategoryService::snapshot)
			.toList();
	}

	@Transactional
	public CategorySnapshot create(UUID userId, String name, int sortOrder) {
		UUID ledgerId = requireAdmin(userId);
		return snapshot(categoryRepository.saveAndFlush(new Category(ledgerId, name, sortOrder)));
	}

	@Transactional
	public CategorySnapshot update(UUID userId, UUID categoryId, String name, int sortOrder, int version) {
		UUID ledgerId = requireAdmin(userId);
		var category = requireCategory(categoryId, ledgerId);
		requireVersion(category, version);
		category.update(name, sortOrder);
		categoryRepository.flush();
		return snapshot(category);
	}

	@Transactional
	public void delete(UUID userId, UUID categoryId, int version) {
		UUID ledgerId = requireAdmin(userId);
		var category = requireCategory(categoryId, ledgerId);
		requireVersion(category, version);
		categoryRepository.delete(category);
		categoryRepository.flush();
	}

	private UUID requireAdmin(UUID userId) {
		var membership = ledgerAccessService.requireActiveMembership(userId);
		if (membership.getRole() != MemberRole.ADMIN) {
			throw new ApiException(HttpStatus.FORBIDDEN, "ADMIN_REQUIRED", "관리자 권한이 필요합니다.");
		}
		return membership.getId().getLedgerId();
	}

	private Category requireCategory(UUID categoryId, UUID ledgerId) {
		return categoryRepository.findByIdAndLedgerId(categoryId, ledgerId)
			.orElseThrow(() -> new ApiException(
				HttpStatus.NOT_FOUND, "CATEGORY_NOT_FOUND", "카테고리를 찾을 수 없습니다."));
	}

	private void requireVersion(Category category, int version) {
		if (category.getVersion() != version) {
			throw new ApiException(
				HttpStatus.CONFLICT, "CATEGORY_VERSION_CONFLICT", "다른 사용자가 이 카테고리를 먼저 변경했습니다.");
		}
	}

	static CategorySnapshot snapshot(Category category) {
		return new CategorySnapshot(
			category.getId(), category.getName(), category.getSortOrder(), category.getVersion());
	}
}
