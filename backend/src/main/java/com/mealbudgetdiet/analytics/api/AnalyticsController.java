package com.mealbudgetdiet.analytics.api;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.UUID;

import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.mealbudgetdiet.analytics.application.AnalyticsService;
import com.mealbudgetdiet.analytics.application.DashboardSnapshot;
import com.mealbudgetdiet.analytics.application.StatisticsSnapshot;
import com.mealbudgetdiet.identity.infrastructure.MealBudgetPrincipal;
import com.mealbudgetdiet.shared.api.ApiException;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;

@Validated
@RestController
public class AnalyticsController {

	private final AnalyticsService analyticsService;

	public AnalyticsController(AnalyticsService analyticsService) {
		this.analyticsService = analyticsService;
	}

	@GetMapping("/api/v1/dashboard")
	DashboardSnapshot dashboard(
		@AuthenticationPrincipal MealBudgetPrincipal principal,
		@RequestParam(required = false) String yearMonth,
		@RequestParam(defaultValue = "5") @Min(1) @Max(20) int recentSize
	) {
		return analyticsService.dashboard(
			principal.id(), yearMonth == null ? null : parseYearMonth(yearMonth), recentSize);
	}

	@GetMapping("/api/v1/statistics")
	StatisticsSnapshot statistics(
		@AuthenticationPrincipal MealBudgetPrincipal principal,
		@RequestParam(required = false) String yearMonth,
		@RequestParam(required = false) LocalDate from,
		@RequestParam(required = false) LocalDate to
	) {
		return analyticsService.statistics(
			principal.id(), yearMonth == null ? null : parseYearMonth(yearMonth), from, to);
	}

	@GetMapping("/api/v1/expenses/export.csv")
	ResponseEntity<byte[]> exportCsv(
		@AuthenticationPrincipal MealBudgetPrincipal principal,
		@RequestParam LocalDate from,
		@RequestParam LocalDate to,
		@RequestParam(required = false) UUID categoryId,
		@RequestParam(required = false) @Size(max = 200) String keyword
	) {
		var export = analyticsService.exportCsv(principal.id(), from, to, categoryId, keyword);
		var disposition = ContentDisposition.attachment()
			.filename(export.filename(), StandardCharsets.UTF_8)
			.build();
		return ResponseEntity.ok()
			.header(HttpHeaders.CONTENT_DISPOSITION, disposition.toString())
			.contentType(new MediaType("text", "csv", StandardCharsets.UTF_8))
			.body(export.content());
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
