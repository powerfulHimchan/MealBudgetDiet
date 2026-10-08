package com.mealbudgetdiet.ai.api;

import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.mealbudgetdiet.ai.application.AiConnectionService;
import com.mealbudgetdiet.ai.application.AiTextResult;
import com.mealbudgetdiet.ai.infrastructure.OpenAiProperties;
import com.mealbudgetdiet.shared.api.ApiException;
import com.mealbudgetdiet.shared.config.SecurityConfig;

@WebMvcTest(AiConnectionController.class)
@Import(SecurityConfig.class)
class AiConnectionControllerTest {

	@Autowired private MockMvc mockMvc;
	@MockitoBean private OpenAiProperties properties;
	@MockitoBean private AiConnectionService connectionService;

	@BeforeEach
	void configure() {
		when(properties.enabled()).thenReturn(true);
		when(properties.isConfigured()).thenReturn(true);
		when(properties.model()).thenReturn("gpt-4.1-mini");
	}

	@Test
	void requiresAuthentication() throws Exception {
		mockMvc.perform(get("/api/v1/admin/ai/status")).andExpect(status().isUnauthorized());
		mockMvc.perform(post("/api/v1/admin/ai/connection-test").with(csrf()))
			.andExpect(status().isUnauthorized());
		verifyNoInteractions(connectionService);
	}

	@Test
	void rejectsGeneralUsersAndLedgerAdmins() throws Exception {
		mockMvc.perform(get("/api/v1/admin/ai/status").with(user("member").roles("USER", "LEDGER_ADMIN")))
			.andExpect(status().isForbidden());
		mockMvc.perform(post("/api/v1/admin/ai/connection-test").with(csrf()).with(user("member").roles("USER")))
			.andExpect(status().isForbidden());
		verifyNoInteractions(connectionService);
	}

	@Test
	void statusIsConfigurationOnlyAndNeverCallsOpenAi() throws Exception {
		mockMvc.perform(get("/api/v1/admin/ai/status").with(user("admin").roles("SERVICE_ADMIN")))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.ready").value(true))
			.andExpect(jsonPath("$.model").value("gpt-4.1-mini"))
			.andExpect(jsonPath("$.apiKey").doesNotExist());
		verifyNoInteractions(connectionService);
	}

	@Test
	void missingKeyIsReportedWithoutFailingStatusRequest() throws Exception {
		when(properties.isConfigured()).thenReturn(false);
		mockMvc.perform(get("/api/v1/admin/ai/status").with(user("admin").roles("SERVICE_ADMIN")))
			.andExpect(status().isOk()).andExpect(jsonPath("$.ready").value(false));
	}

	@Test
	void testRequiresCsrf() throws Exception {
		mockMvc.perform(post("/api/v1/admin/ai/connection-test").with(user("admin").roles("SERVICE_ADMIN")))
			.andExpect(status().isForbidden());
		verifyNoInteractions(connectionService);
	}

	@Test
	void adminReceivesTextAndTokenCounts() throws Exception {
		when(connectionService.testConnection()).thenReturn(new AiTextResult("gpt-4.1-mini", "Sikbi AI 연결 성공", 25, 8));
		mockMvc.perform(post("/api/v1/admin/ai/connection-test").with(csrf()).with(user("admin").roles("SERVICE_ADMIN")))
			.andExpect(status().isOk()).andExpect(jsonPath("$.text").value("Sikbi AI 연결 성공"))
			.andExpect(jsonPath("$.inputTokens").value(25));
	}

	@Test
	void returnsExistingProblemFormatForDisabledIntegration() throws Exception {
		when(connectionService.testConnection()).thenThrow(new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "AI_DISABLED", "비활성화"));
		mockMvc.perform(post("/api/v1/admin/ai/connection-test").with(csrf()).with(user("admin").roles("SERVICE_ADMIN")))
			.andExpect(status().isServiceUnavailable()).andExpect(jsonPath("$.code").value("AI_DISABLED"));
	}
}
