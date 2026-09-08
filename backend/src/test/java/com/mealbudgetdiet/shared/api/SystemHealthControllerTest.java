package com.mealbudgetdiet.shared.api;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class SystemHealthControllerTest {

	private final SystemHealthController controller = new SystemHealthController();

	@Test
	void returnsServiceHealth() {
		var response = controller.health();

		assertThat(response.status()).isEqualTo("UP");
		assertThat(response.service()).isEqualTo("meal-budget-diet");
	}
}
