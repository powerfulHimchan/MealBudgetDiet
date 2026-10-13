package com.mealbudgetdiet.analytics.api;

import java.time.LocalDate;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import com.mealbudgetdiet.analytics.application.MidCycleAnalysisService;
import com.mealbudgetdiet.analytics.application.MidCycleAnalysisSnapshot;
import com.mealbudgetdiet.identity.infrastructure.MealBudgetPrincipal;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

@RestController
public class MidCycleAnalysisController {
	private final MidCycleAnalysisService service;

	public MidCycleAnalysisController(MidCycleAnalysisService service) {
		this.service = service;
	}

	@GetMapping("/api/v1/dashboard/mid-cycle-analysis")
	MidCycleAnalysisSnapshot snapshot(@AuthenticationPrincipal MealBudgetPrincipal principal,
		@RequestParam LocalDate from, @RequestParam LocalDate to) {
		return service.snapshot(principal.id(), from, to);
	}

	@PostMapping("/api/v1/dashboard/mid-cycle-analysis")
	MidCycleAnalysisSnapshot analyze(@AuthenticationPrincipal MealBudgetPrincipal principal,
		@Valid @RequestBody AnalysisRequest request) {
		return service.analyze(principal.id(), request.from(), request.to());
	}

	public record AnalysisRequest(@NotNull LocalDate from, @NotNull LocalDate to) {
	}
}
