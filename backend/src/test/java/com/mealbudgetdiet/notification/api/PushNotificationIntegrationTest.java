package com.mealbudgetdiet.notification.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import com.jayway.jsonpath.JsonPath;
import com.mealbudgetdiet.TestcontainersConfiguration;

import jakarta.servlet.http.Cookie;

@Import({ TestcontainersConfiguration.class, PushNotificationIntegrationTest.FixedClockConfig.class })
@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = {
	"app.bootstrap-token=push-bootstrap-token",
	"app.push.vapid-public-key=test-public-key",
	"app.push.dispatch-enabled=false"
})
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class PushNotificationIntegrationTest {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private JdbcTemplate jdbcTemplate;

	@Test
	void registersDevicesAndCreatesOneMonthlyAlertForAllActiveSubscriptions() throws Exception {
		Cookie adminSession = bootstrapAdmin();
		Cookie memberSession = registerMember(adminSession);
		String categoryId = firstCategoryId(adminSession);

		mockMvc.perform(get("/api/v1/push/vapid-public-key").cookie(memberSession))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.publicKey").value("test-public-key"));

		mockMvc.perform(put("/api/v1/push-subscriptions")
				.with(csrf()).cookie(memberSession)
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{"endpoint":"https://127.0.0.1/internal","keys":{"p256dh":"key","auth":"auth"}}
					"""))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("PUSH_ENDPOINT_INVALID"));

		String adminSubscriptionId = registerSubscription(
			adminSession, "https://updates.push.services.mozilla.com/wpush/v2/admin-device", true);
		String memberSubscriptionId = registerSubscription(
			memberSession, "https://updates.push.services.mozilla.com/wpush/v2/member-device", true);

		registerSubscription(
			adminSession, "https://updates.push.services.mozilla.com/wpush/v2/admin-device", false);

		mockMvc.perform(put("/api/v1/ledger/settings/budget-cycle")
				.with(csrf()).cookie(adminSession)
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"startDay\":10,\"version\":0}"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.budgetCycleStartDay").value(10))
			.andExpect(jsonPath("$.version").value(1));

		mockMvc.perform(put("/api/v1/ledger/settings/push-threshold")
				.with(csrf()).cookie(memberSession)
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"usageThreshold\":90,\"version\":1}"))
			.andExpect(status().isForbidden())
			.andExpect(jsonPath("$.code").value("ADMIN_REQUIRED"));

		mockMvc.perform(put("/api/v1/ledger/settings/push-threshold")
				.with(csrf()).cookie(adminSession)
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"usageThreshold\":101,\"version\":1}"))
			.andExpect(status().isBadRequest());

		mockMvc.perform(put("/api/v1/ledger/settings/push-threshold")
				.with(csrf()).cookie(adminSession)
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"usageThreshold\":90,\"version\":1}"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.pushUsageThreshold").value(90))
			.andExpect(jsonPath("$.version").value(2));

		createExpense(adminSession, categoryId, 79_000, "2026-09-15");
		assertThat(count("budget_alerts")).isZero();

		createExpense(memberSession, categoryId, 6_000, "2026-09-15");
		assertThat(count("budget_alerts")).isZero();

		createExpense(memberSession, categoryId, 5_000, "2026-09-15");
		assertThat(count("budget_alerts")).isOne();
		assertThat(count("push_deliveries")).isEqualTo(2);
		assertThat(jdbcTemplate.queryForObject(
			"select total_spent from budget_alerts", Long.class)).isEqualTo(90_000);
		assertThat(jdbcTemplate.queryForObject(
			"select usage_threshold from budget_alerts", Integer.class)).isEqualTo(90);
		assertThat(jdbcTemplate.queryForObject(
			"select remaining_days from budget_alerts", Integer.class)).isEqualTo(25);
		assertThat(jdbcTemplate.queryForObject(
			"select cycle_days from budget_alerts", Integer.class)).isEqualTo(30);
		assertThat(jdbcTemplate.queryForObject(
			"select alert_month from budget_alerts", java.time.LocalDate.class))
			.isEqualTo(java.time.LocalDate.of(2026, 9, 1));
		assertThat(jdbcTemplate.queryForObject(
			"select alert_type from budget_alerts", String.class)).isEqualTo("MONTHLY_BUDGET_OVERRUN_RISK");

		createExpense(adminSession, categoryId, 1_000, "2026-09-15");
		assertThat(count("budget_alerts")).isOne();
		assertThat(count("push_deliveries")).isEqualTo(2);

		mockMvc.perform(delete("/api/v1/push-subscriptions/{id}", memberSubscriptionId)
			.with(csrf()).cookie(adminSession))
			.andExpect(status().isNotFound())
			.andExpect(jsonPath("$.code").value("PUSH_SUBSCRIPTION_NOT_FOUND"));

		mockMvc.perform(delete("/api/v1/push-subscriptions/{id}", memberSubscriptionId)
			.with(csrf()).cookie(memberSession))
			.andExpect(status().isNoContent());
		assertThat(jdbcTemplate.queryForObject(
			"select status from push_subscriptions where id = ?::uuid", String.class, memberSubscriptionId))
			.isEqualTo("DISABLED");
		assertThat(adminSubscriptionId).isNotBlank();
	}

	private String registerSubscription(Cookie session, String endpoint, boolean created) throws Exception {
		var result = mockMvc.perform(put("/api/v1/push-subscriptions")
				.with(csrf())
				.cookie(session)
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{
					  "endpoint":"%s",
					  "expirationTime":null,
					  "keys":{"p256dh":"test-p256dh-key","auth":"test-auth-key"}
					}
					""".formatted(endpoint)))
			.andExpect(created ? status().isCreated() : status().isOk())
			.andExpect(jsonPath("$.status").value("ACTIVE"))
			.andReturn();
		return JsonPath.read(result.getResponse().getContentAsString(), "$.id");
	}

	private void createExpense(Cookie session, String categoryId, long amount, String spentOn) throws Exception {
		mockMvc.perform(post("/api/v1/expenses")
				.with(csrf())
				.cookie(session)
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{
					  "amount":%d,
					  "spentOn":"%s",
					  "categoryId":"%s",
					  "merchant":"테스트 마트",
					  "memo":"푸시 조건 검증",
					  "imageIds":[]
					}
					""".formatted(amount, spentOn, categoryId)))
			.andExpect(status().isCreated());
	}

	private Cookie bootstrapAdmin() throws Exception {
		var result = mockMvc.perform(post("/api/v1/bootstrap/admin")
				.with(csrf())
				.header("X-Bootstrap-Token", "push-bootstrap-token")
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{
					  "email":"owner-push@example.com",
					  "password":"owner-password!",
					  "displayName":"장부 관리자",
					  "ledgerName":"우리집 식비",
					  "defaultMonthlyBudget":100000
					}
					"""))
			.andExpect(status().isCreated())
			.andReturn();
		return sessionCookie(result.getResponse().getHeader("Set-Cookie"));
	}

	private Cookie registerMember(Cookie adminSession) throws Exception {
		var invitation = mockMvc.perform(post("/api/v1/invitations").with(csrf()).cookie(adminSession))
			.andExpect(status().isCreated())
			.andReturn();
		String code = JsonPath.read(invitation.getResponse().getContentAsString(), "$.code");
		var result = mockMvc.perform(post("/api/v1/auth/register")
				.with(csrf())
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{
					  "inviteCode":"%s",
					  "email":"member-push@example.com",
					  "password":"member-password!",
					  "displayName":"가족 구성원"
					}
					""".formatted(code)))
			.andExpect(status().isCreated())
			.andReturn();
		return sessionCookie(result.getResponse().getHeader("Set-Cookie"));
	}

	private String firstCategoryId(Cookie session) throws Exception {
		var result = mockMvc.perform(get("/api/v1/categories").cookie(session))
			.andExpect(status().isOk())
			.andReturn();
		return JsonPath.read(result.getResponse().getContentAsString(), "$.items[0].id");
	}

	private long count(String table) {
		return jdbcTemplate.queryForObject("select count(*) from " + table, Long.class);
	}

	private Cookie sessionCookie(String setCookieHeader) {
		assertThat(setCookieHeader).isNotNull().contains("MBD_SESSION=");
		String prefix = "MBD_SESSION=";
		int valueStart = setCookieHeader.indexOf(prefix) + prefix.length();
		int valueEnd = setCookieHeader.indexOf(';', valueStart);
		return new Cookie("MBD_SESSION", setCookieHeader.substring(valueStart, valueEnd));
	}

	@TestConfiguration(proxyBeanMethods = false)
	static class FixedClockConfig {
		@Bean
		@Primary
		Clock fixedClock() {
			return Clock.fixed(Instant.parse("2026-09-15T03:00:00Z"), ZoneOffset.UTC);
		}
	}
}
