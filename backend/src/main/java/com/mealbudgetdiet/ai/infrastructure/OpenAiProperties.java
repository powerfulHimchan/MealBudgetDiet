package com.mealbudgetdiet.ai.infrastructure;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

@Validated
@ConfigurationProperties("app.ai.openai")
public record OpenAiProperties(
	boolean enabled,
	String apiKey,
	@NotBlank String model,
	@NotNull Duration connectTimeout,
	@NotNull Duration requestTimeout,
	@Min(16) @Max(4096) int maxOutputTokens
) {

	public boolean isConfigured() {
		return apiKey != null && !apiKey.isBlank();
	}

	@AssertTrue(message = "OpenAI timeouts must be between 1 and 120 seconds")
	public boolean isTimeoutsValid() {
		return validTimeout(connectTimeout) && validTimeout(requestTimeout);
	}

	private static boolean validTimeout(Duration timeout) {
		return timeout != null && timeout.compareTo(Duration.ofSeconds(1)) >= 0
			&& timeout.compareTo(Duration.ofSeconds(120)) <= 0;
	}

	@Override
	public String toString() {
		return "OpenAiProperties[enabled=" + enabled + ", model=" + model + ", apiKey=REDACTED]";
	}
}
