package com.mealbudgetdiet.ai.application;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import com.mealbudgetdiet.ai.infrastructure.OpenAiGateway;
import com.mealbudgetdiet.shared.api.ApiException;

@Service
public class AiConnectionService {

	private final OpenAiGateway gateway;
	private final Clock clock;
	private Instant windowStartedAt;
	private int attempts;

	public AiConnectionService(OpenAiGateway gateway, Clock clock) {
		this.gateway = gateway;
		this.clock = clock;
	}

	public AiTextResult testConnection() {
		gateway.requireReady();
		reserveAttempt();
		return gateway.generate("You are checking the Sikbi server's OpenAI connection. Reply briefly in Korean.",
			"연결 확인용 요청입니다. 'Sikbi AI 연결 성공'이라고 답해 주세요.");
	}

	private synchronized void reserveAttempt() {
		var now = clock.instant();
		if (windowStartedAt == null || !now.isBefore(windowStartedAt.plus(Duration.ofMinutes(1)))) {
			windowStartedAt = now;
			attempts = 0;
		}
		if (attempts >= 5) {
			throw new ApiException(HttpStatus.TOO_MANY_REQUESTS, "AI_TEST_RATE_LIMITED", "연결 테스트는 서버당 1분에 5회까지 가능합니다.");
		}
		attempts++;
	}
}
