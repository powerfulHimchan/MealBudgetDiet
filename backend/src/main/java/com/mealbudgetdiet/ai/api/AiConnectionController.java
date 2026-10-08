package com.mealbudgetdiet.ai.api;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

import com.mealbudgetdiet.ai.application.AiConnectionService;
import com.mealbudgetdiet.ai.application.AiTextResult;
import com.mealbudgetdiet.ai.infrastructure.OpenAiProperties;

@RestController
public class AiConnectionController {

	private final OpenAiProperties properties;
	private final AiConnectionService connectionService;

	public AiConnectionController(OpenAiProperties properties, AiConnectionService connectionService) {
		this.properties = properties;
		this.connectionService = connectionService;
	}

	@GetMapping("/api/v1/admin/ai/status")
	StatusResponse status() {
		return new StatusResponse(properties.enabled(), properties.isConfigured(),
			properties.enabled() && properties.isConfigured(), properties.model());
	}

	@PostMapping("/api/v1/admin/ai/connection-test")
	AiTextResult testConnection() {
		return connectionService.testConnection();
	}

	public record StatusResponse(boolean enabled, boolean configured, boolean ready, String model) {
	}
}
