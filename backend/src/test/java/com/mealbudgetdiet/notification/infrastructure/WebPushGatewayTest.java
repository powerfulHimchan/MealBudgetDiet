package com.mealbudgetdiet.notification.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class WebPushGatewayTest {

	@Test
	void retriesOnlyTransientHttpFailures() {
		assertThat(WebPushGateway.isRetryableStatus(408)).isTrue();
		assertThat(WebPushGateway.isRetryableStatus(425)).isTrue();
		assertThat(WebPushGateway.isRetryableStatus(429)).isTrue();
		assertThat(WebPushGateway.isRetryableStatus(503)).isTrue();
		assertThat(WebPushGateway.isRetryableStatus(400)).isFalse();
		assertThat(WebPushGateway.isRetryableStatus(403)).isFalse();
	}

	@Test
	void expiresOnlyGoneSubscriptions() {
		assertThat(WebPushGateway.isExpiredStatus(404)).isTrue();
		assertThat(WebPushGateway.isExpiredStatus(410)).isTrue();
		assertThat(WebPushGateway.isExpiredStatus(429)).isFalse();
	}
}
