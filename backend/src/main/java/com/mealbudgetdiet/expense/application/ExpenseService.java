package com.mealbudgetdiet.expense.application;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Base64;
import java.util.Locale;
import java.util.UUID;
import java.util.List;

import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.mealbudgetdiet.expense.domain.Category;
import com.mealbudgetdiet.expense.domain.Expense;
import com.mealbudgetdiet.expense.infrastructure.CategoryRepository;
import com.mealbudgetdiet.expense.infrastructure.ExpenseRepository;
import com.mealbudgetdiet.ledger.application.LedgerAccessService;
import com.mealbudgetdiet.notification.application.BudgetAlertService;
import com.mealbudgetdiet.media.application.ImageService;
import com.mealbudgetdiet.shared.api.ApiException;

@Service
public class ExpenseService {
	private static final LocalDate EARLIEST_SUPPORTED_DATE = LocalDate.of(1, 1, 1);
	private static final LocalDate LATEST_SUPPORTED_DATE = LocalDate.of(9999, 12, 31);
	private static final Instant LATEST_SUPPORTED_INSTANT = Instant.parse("9999-12-31T23:59:59Z");
	private static final UUID EMPTY_UUID = new UUID(0, 0);

	private final LedgerAccessService ledgerAccessService;
	private final ExpenseRepository expenseRepository;
	private final CategoryRepository categoryRepository;
	private final BudgetAlertService budgetAlertService;
	private final ImageService imageService;

	public ExpenseService(
		LedgerAccessService ledgerAccessService,
		ExpenseRepository expenseRepository,
		CategoryRepository categoryRepository,
		BudgetAlertService budgetAlertService,
		ImageService imageService
	) {
		this.ledgerAccessService = ledgerAccessService;
		this.expenseRepository = expenseRepository;
		this.categoryRepository = categoryRepository;
		this.budgetAlertService = budgetAlertService;
		this.imageService = imageService;
	}

	@Transactional
	public ExpenseSnapshot create(
		UUID userId,
		long amount,
		LocalDate spentOn,
		UUID categoryId,
		String merchant,
		String memo,
		List<UUID> imageIds
	) {
		UUID ledgerId = ledgerId(userId);
		Category category = requireCategory(categoryId, ledgerId);
		var expense = expenseRepository.saveAndFlush(
			new Expense(ledgerId, category, amount, spentOn, merchant, memo));
		imageService.attachExpenseImages(userId, ledgerId, expense.getId(), imageIds);
		budgetAlertService.evaluateNewExpense(userId, expense);
		return snapshot(expense);
	}

	@Transactional(readOnly = true)
	public ExpensePage search(
		UUID userId,
		LocalDate from,
		LocalDate to,
		UUID categoryId,
		boolean uncategorized,
		String keyword,
		int size,
		String cursorValue
	) {
		if (from != null && to != null && from.isAfter(to)) {
			throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_DATE_RANGE", "시작일은 종료일보다 늦을 수 없습니다.");
		}
		if (categoryId != null && uncategorized) {
			throw new ApiException(
				HttpStatus.BAD_REQUEST, "INVALID_CATEGORY_FILTER", "카테고리와 분류 없음 필터를 동시에 사용할 수 없습니다.");
		}

		UUID ledgerId = ledgerId(userId);
		if (categoryId != null) {
			requireCategory(categoryId, ledgerId);
		}
		ExpenseCursor cursor = decodeCursor(cursorValue);
		String normalizedKeyword = keyword == null || keyword.isBlank() ? null : keyword.strip();
		String keywordPattern = normalizedKeyword == null
			? "%"
			: "%" + normalizedKeyword.toLowerCase(Locale.ROOT) + "%";
		var found = expenseRepository.search(
			ledgerId,
			from == null ? EARLIEST_SUPPORTED_DATE : from,
			to == null ? LATEST_SUPPORTED_DATE : to,
			categoryId != null,
			categoryId == null ? EMPTY_UUID : categoryId,
			uncategorized,
			keywordPattern,
			cursor != null,
			cursor == null ? LATEST_SUPPORTED_DATE : cursor.spentOn(),
			cursor == null ? LATEST_SUPPORTED_INSTANT : cursor.createdAt(),
			cursor == null ? EMPTY_UUID : cursor.id(),
			PageRequest.of(0, size + 1)
		);
		boolean hasNext = found.size() > size;
		var items = found.stream().limit(size).map(this::snapshot).toList();
		String nextCursor = hasNext ? encodeCursor(found.get(size - 1)) : null;
		return new ExpensePage(items, nextCursor, hasNext);
	}

	@Transactional(readOnly = true)
	public ExpenseSnapshot get(UUID userId, UUID expenseId) {
		return snapshot(requireExpense(expenseId, ledgerId(userId)));
	}

	@Transactional
	public ExpenseSnapshot update(
		UUID userId,
		UUID expenseId,
		long amount,
		LocalDate spentOn,
		UUID categoryId,
		String merchant,
		String memo,
		List<UUID> imageIds,
		int version
	) {
		UUID ledgerId = ledgerId(userId);
		var expense = requireExpense(expenseId, ledgerId);
		requireVersion(expense, version);
		expense.update(requireCategory(categoryId, ledgerId), amount, spentOn, merchant, memo);
		imageService.attachExpenseImages(userId, ledgerId, expense.getId(), imageIds);
		expenseRepository.flush();
		return snapshot(expense);
	}

	@Transactional
	public void delete(UUID userId, UUID expenseId, int version) {
		var expense = requireExpense(expenseId, ledgerId(userId));
		requireVersion(expense, version);
		imageService.deleteExpenseImages(expenseId);
		expenseRepository.delete(expense);
		expenseRepository.flush();
	}

	private UUID ledgerId(UUID userId) {
		return ledgerAccessService.requireActiveMembership(userId).getId().getLedgerId();
	}

	private Category requireCategory(UUID categoryId, UUID ledgerId) {
		return categoryRepository.findByIdAndLedgerId(categoryId, ledgerId)
			.orElseThrow(() -> new ApiException(
				HttpStatus.BAD_REQUEST, "CATEGORY_INVALID", "현재 장부에서 사용할 수 없는 카테고리입니다."));
	}

	private Expense requireExpense(UUID expenseId, UUID ledgerId) {
		return expenseRepository.findByIdAndLedgerId(expenseId, ledgerId)
			.orElseThrow(() -> new ApiException(
				HttpStatus.NOT_FOUND, "EXPENSE_NOT_FOUND", "식비 내역을 찾을 수 없습니다."));
	}

	private void requireVersion(Expense expense, int version) {
		if (expense.getVersion() != version) {
			throw new ApiException(
				HttpStatus.CONFLICT, "EXPENSE_VERSION_CONFLICT", "다른 사용자가 이 식비 내역을 먼저 변경했습니다.");
		}
	}

	private ExpenseSnapshot snapshot(Expense expense) {
		return new ExpenseSnapshot(
			expense.getId(),
			expense.getAmount(),
			expense.getSpentOn(),
			expense.getCategory() == null ? null : CategoryService.snapshot(expense.getCategory()),
			expense.getMerchant(),
			expense.getMemo(),
			imageService.expenseImages(expense.getId()),
			expense.getVersion(),
			expense.getCreatedAt(),
			expense.getUpdatedAt()
		);
	}

	private static String encodeCursor(Expense expense) {
		String raw = "%s|%s|%s".formatted(expense.getSpentOn(), expense.getCreatedAt(), expense.getId());
		return Base64.getUrlEncoder().withoutPadding().encodeToString(raw.getBytes(StandardCharsets.UTF_8));
	}

	private static ExpenseCursor decodeCursor(String value) {
		if (value == null || value.isBlank()) {
			return null;
		}
		try {
			String decoded = new String(Base64.getUrlDecoder().decode(value), StandardCharsets.UTF_8);
			String[] parts = decoded.split("\\|", -1);
			if (parts.length != 3) {
				throw new IllegalArgumentException("wrong part count");
			}
			return new ExpenseCursor(
				LocalDate.parse(parts[0]), Instant.parse(parts[1]), UUID.fromString(parts[2]));
		} catch (IllegalArgumentException exception) {
			throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_EXPENSE_CURSOR", "목록 커서가 올바르지 않습니다.");
		}
	}

	private record ExpenseCursor(LocalDate spentOn, Instant createdAt, UUID id) {
	}
}
