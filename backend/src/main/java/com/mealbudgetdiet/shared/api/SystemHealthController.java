package com.mealbudgetdiet.shared.api;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/system")
public class SystemHealthController {

	@GetMapping("/health")
	public HealthResponse health() {
		return new HealthResponse("UP", "meal-budget-diet");
	}

	public record HealthResponse(String status, String service) {
	}
}
