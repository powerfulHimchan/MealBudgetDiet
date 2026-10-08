package com.mealbudgetdiet.ai.application;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;

import org.junit.jupiter.api.Test;

import com.mealbudgetdiet.ai.infrastructure.OpenAiGateway;
import com.mealbudgetdiet.shared.api.ApiException;

class AiConnectionServiceTest {

	@Test
	void capsConnectionChecksAndAllowsNextMinute() {
		var gateway = mock(OpenAiGateway.class);
		var clock = mock(Clock.class);
		var now = Instant.parse("2026-10-08T01:00:00Z");
		when(clock.instant()).thenReturn(now);
		var service = new AiConnectionService(gateway, clock);
		for (int attempt = 0; attempt < 5; attempt++) {
			service.testConnection();
		}
		assertThatThrownBy(service::testConnection).isInstanceOfSatisfying(ApiException.class, exception ->
			org.assertj.core.api.Assertions.assertThat(exception.getCode()).isEqualTo("AI_TEST_RATE_LIMITED"));
		verify(gateway, times(5)).generate(anyString(), anyString());
		when(clock.instant()).thenReturn(now.plusSeconds(60));
		service.testConnection();
		verify(gateway, times(6)).generate(anyString(), anyString());
	}
}
