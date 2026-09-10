package com.mealbudgetdiet.identity.application;

import org.springframework.http.HttpStatus;

import com.mealbudgetdiet.shared.api.ApiException;

public class AuthRateLimitException extends ApiException {

	private final long retryAfterSeconds;

	public AuthRateLimitException(long retryAfterSeconds) {
		super(
			HttpStatus.TOO_MANY_REQUESTS,
			"AUTH_RATE_LIMITED",
			"인증 요청이 너무 많습니다. 잠시 후 다시 시도해 주세요."
		);
		this.retryAfterSeconds = Math.max(1, retryAfterSeconds);
	}

	public long getRetryAfterSeconds() {
		return retryAfterSeconds;
	}
}
