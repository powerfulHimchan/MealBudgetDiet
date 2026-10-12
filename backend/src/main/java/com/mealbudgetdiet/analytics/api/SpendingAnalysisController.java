package com.mealbudgetdiet.analytics.api;

import java.time.LocalDate;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import com.mealbudgetdiet.analytics.application.SpendingAnalysisService;
import com.mealbudgetdiet.identity.infrastructure.MealBudgetPrincipal;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

@RestController
public class SpendingAnalysisController {
	private final SpendingAnalysisService service;

	public SpendingAnalysisController(SpendingAnalysisService service) {
		this.service = service;
	}

	@PostMapping("/api/v1/statistics/analysis")
	SpendingAnalysisService.AnalysisResponse analyze(@AuthenticationPrincipal MealBudgetPrincipal principal,
		@Valid @RequestBody AnalysisRequest request) {
		return service.analyze(principal.id(), request.from(), request.to());
	}

	public record AnalysisRequest(@NotNull LocalDate from, @NotNull LocalDate to) {
	}
}
