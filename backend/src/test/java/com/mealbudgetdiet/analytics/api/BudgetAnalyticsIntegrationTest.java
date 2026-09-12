package com.mealbudgetdiet.analytics.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import com.jayway.jsonpath.JsonPath;
import com.mealbudgetdiet.TestcontainersConfiguration;

import jakarta.servlet.http.Cookie;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = "app.bootstrap-token=analytics-bootstrap-token")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class BudgetAnalyticsIntegrationTest {

	@Autowired
	private MockMvc mockMvc;

	@Test
	void managesBudgetsAndBuildsDashboardStatisticsAndSafeCsv() throws Exception {
		Cookie adminSession = bootstrapAdmin();
		Cookie memberSession = registerMember(adminSession);
		String categoryId = firstCategoryId(adminSession);

		mockMvc.perform(put("/api/v1/budgets/default")
				.with(csrf())
				.cookie(memberSession)
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"amount\":900000,\"version\":0}"))
			.andExpect(status().isForbidden())
			.andExpect(jsonPath("$.code").value("ADMIN_REQUIRED"));

		mockMvc.perform(put("/api/v1/budgets/default")
				.with(csrf())
				.cookie(adminSession)
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"amount\":900000,\"version\":0}"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.amount").value(900000))
			.andExpect(jsonPath("$.source").value("DEFAULT"))
			.andExpect(jsonPath("$.version").value(1));

		mockMvc.perform(put("/api/v1/budgets/default")
				.with(csrf())
				.cookie(adminSession)
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"amount\":950000,\"version\":0}"))
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("BUDGET_VERSION_CONFLICT"));

		mockMvc.perform(get("/api/v1/budgets/2026-09").cookie(adminSession))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.amount").value(900000))
			.andExpect(jsonPath("$.source").value("DEFAULT"));

		mockMvc.perform(put("/api/v1/budgets/2026-09")
				.with(csrf())
				.cookie(adminSession)
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"amount\":300000,\"version\":0}"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.amount").value(300000))
			.andExpect(jsonPath("$.source").value("MONTHLY_OVERRIDE"))
			.andExpect(jsonPath("$.version").value(0));

		createExpense(memberSession, categoryId, 100000, "2026-09-01", "=HYPERLINK(\"https://bad\")", "장보기");
		createExpense(adminSession, categoryId, 80000, "2026-09-02", "동네마트", "추가 장보기");
		createExpense(adminSession, categoryId, 60000, "2026-08-15", "지난달 마트", "비교 내역");

		mockMvc.perform(get("/api/v1/dashboard")
				.cookie(memberSession)
				.param("yearMonth", "2026-09")
				.param("recentSize", "5"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.budget").value(300000))
			.andExpect(jsonPath("$.spent").value(180000))
			.andExpect(jsonPath("$.remaining").value(120000))
			.andExpect(jsonPath("$.projectedSpent").isNumber())
			.andExpect(jsonPath("$.usageRate").value(60.0))
			.andExpect(jsonPath("$.status").value("NORMAL"))
			.andExpect(jsonPath("$.recentExpenses.length()").value(2))
			.andExpect(jsonPath("$.recentExpenses[0].merchant").value("동네마트"));

		mockMvc.perform(put("/api/v1/ledger/settings/push-threshold")
				.with(csrf())
				.cookie(adminSession)
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"usageThreshold\":50,\"version\":1}"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.pushUsageThreshold").value(50))
			.andExpect(jsonPath("$.version").value(2));

		mockMvc.perform(get("/api/v1/dashboard")
				.cookie(memberSession)
				.param("yearMonth", "2026-09"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.usageRate").value(60.0))
			.andExpect(jsonPath("$.pushUsageThreshold").value(50))
			.andExpect(jsonPath("$.status").value("WARNING"));

		mockMvc.perform(get("/api/v1/statistics")
				.cookie(adminSession)
				.param("from", "2026-09-01")
				.param("to", "2026-09-30"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.totalAmount").value(180000))
			.andExpect(jsonPath("$.budget.amount").value(300000))
			.andExpect(jsonPath("$.budget.usageRate").value(60.0))
			.andExpect(jsonPath("$.comparison.from").value("2026-08-01"))
			.andExpect(jsonPath("$.comparison.to").value("2026-08-31"))
			.andExpect(jsonPath("$.comparison.totalAmount").value(60000))
			.andExpect(jsonPath("$.comparison.changeAmount").value(120000))
			.andExpect(jsonPath("$.comparison.changeRate").value(200.0))
			.andExpect(jsonPath("$.daily.length()").value(2))
			.andExpect(jsonPath("$.categories[0].categoryName").value("장보기"))
			.andExpect(jsonPath("$.categories[0].ratio").value(100.0));

		var csvResult = mockMvc.perform(get("/api/v1/expenses/export.csv")
				.cookie(memberSession)
				.param("from", "2026-09-01")
				.param("to", "2026-09-30"))
			.andExpect(status().isOk())
			.andExpect(content().contentType("text/csv;charset=UTF-8"))
			.andExpect(header().string("Content-Disposition", org.hamcrest.Matchers.containsString("attachment")))
			.andReturn();
		byte[] csvBytes = csvResult.getResponse().getContentAsByteArray();
		assertThat(csvBytes).startsWith((byte) 0xEF, (byte) 0xBB, (byte) 0xBF);
		assertThat(new String(csvBytes, StandardCharsets.UTF_8))
			.contains("사용일,금액,카테고리,상호명,메모")
			.contains("\"'=HYPERLINK(\"\"https://bad\"\")\"");

		mockMvc.perform(put("/api/v1/ledger/settings/budget-cycle")
				.with(csrf())
				.cookie(memberSession)
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"startDay\":25,\"version\":2}"))
			.andExpect(status().isForbidden())
			.andExpect(jsonPath("$.code").value("ADMIN_REQUIRED"));

		mockMvc.perform(put("/api/v1/ledger/settings/budget-cycle")
				.with(csrf())
				.cookie(adminSession)
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"startDay\":32,\"version\":2}"))
			.andExpect(status().isBadRequest());

		mockMvc.perform(put("/api/v1/ledger/settings/budget-cycle")
				.with(csrf())
				.cookie(adminSession)
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"startDay\":25,\"version\":2}"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.budgetCycleStartDay").value(25))
			.andExpect(jsonPath("$.version").value(3));

		mockMvc.perform(get("/api/v1/ledger").cookie(memberSession))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.budgetCycleStartDay").value(25))
			.andExpect(jsonPath("$.version").value(3));

		createExpense(adminSession, categoryId, 50000, "2026-10-08", "시장", "새 예산 주기");

		mockMvc.perform(get("/api/v1/dashboard")
				.cookie(memberSession)
				.param("yearMonth", "2026-09"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.period.from").value("2026-09-25"))
			.andExpect(jsonPath("$.period.to").value("2026-10-24"))
			.andExpect(jsonPath("$.budget").value(300000))
			.andExpect(jsonPath("$.spent").value(50000))
			.andExpect(jsonPath("$.recentExpenses.length()").value(1));

		mockMvc.perform(get("/api/v1/statistics")
				.cookie(adminSession)
				.param("yearMonth", "2026-09"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.period.from").value("2026-09-25"))
			.andExpect(jsonPath("$.period.to").value("2026-10-24"))
			.andExpect(jsonPath("$.totalAmount").value(50000))
			.andExpect(jsonPath("$.budget.amount").value(300000))
			.andExpect(jsonPath("$.comparison.from").value("2026-08-25"))
			.andExpect(jsonPath("$.comparison.to").value("2026-09-24"))
			.andExpect(jsonPath("$.comparison.totalAmount").value(180000));

		mockMvc.perform(delete("/api/v1/budgets/2026-09")
				.with(csrf())
				.cookie(adminSession)
				.param("version", "0"))
			.andExpect(status().isNoContent());

		mockMvc.perform(get("/api/v1/budgets/2026-09").cookie(memberSession))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.amount").value(900000))
			.andExpect(jsonPath("$.source").value("DEFAULT"));

		mockMvc.perform(put("/api/v1/ledger/settings/budget-cycle")
				.with(csrf()).cookie(adminSession).contentType(MediaType.APPLICATION_JSON)
				.content("{\"unit\":\"WEEKLY\",\"startDay\":25,\"weekStartDay\":4,\"version\":3}"))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("WEEKLY_BUDGET_REQUIRED"));

		mockMvc.perform(put("/api/v1/ledger/settings/budget-cycle")
				.with(csrf()).cookie(adminSession).contentType(MediaType.APPLICATION_JSON)
				.content("{\"unit\":\"WEEKLY\",\"startDay\":25,\"weekStartDay\":4,\"weeklyBudget\":200000,\"version\":3}"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.budgetCycleUnit").value("WEEKLY"))
			.andExpect(jsonPath("$.defaultWeeklyBudget").value(200000))
			.andExpect(jsonPath("$.version").value(4));

		mockMvc.perform(get("/api/v1/budgets/current").cookie(memberSession))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.amount").value(200000))
			.andExpect(jsonPath("$.source").value("WEEKLY_DEFAULT"));

		mockMvc.perform(get("/api/v1/statistics").cookie(adminSession)
				.param("from", "2026-10-08").param("to", "2026-10-14"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.totalAmount").value(50000))
			.andExpect(jsonPath("$.budget.amount").value(200000))
			.andExpect(jsonPath("$.comparison.from").value("2026-10-01"))
			.andExpect(jsonPath("$.comparison.to").value("2026-10-07"));

		mockMvc.perform(get("/api/v1/statistics").cookie(adminSession)
				.param("from", "2026-10-08").param("to", "2026-10-09"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.budget.amount").value(57143));

		mockMvc.perform(get("/api/v1/dashboard").cookie(memberSession)
				.param("yearMonth", "2026-09"))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("MONTHLY_CYCLE_ONLY"));

		mockMvc.perform(put("/api/v1/ledger/settings/budget-cycle")
				.with(csrf()).cookie(adminSession).contentType(MediaType.APPLICATION_JSON)
				.content("{\"unit\":\"MONTHLY\",\"startDay\":25,\"weekStartDay\":4,\"version\":4}"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.defaultWeeklyBudget").value(200000));
	}

	private Cookie bootstrapAdmin() throws Exception {
		var result = mockMvc.perform(post("/api/v1/bootstrap/admin")
				.with(csrf())
				.header("X-Bootstrap-Token", "analytics-bootstrap-token")
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{
					  "email":"owner-analytics@example.com",
					  "password":"owner-password!",
					  "displayName":"장부 관리자",
					  "ledgerName":"우리집 식비",
					  "defaultMonthlyBudget":800000
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
					  "email":"member-analytics@example.com",
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

	private void createExpense(
		Cookie session,
		String categoryId,
		long amount,
		String spentOn,
		String merchant,
		String memo
	) throws Exception {
		String body = new tools.jackson.databind.ObjectMapper().writeValueAsString(java.util.Map.of(
			"amount", amount,
			"spentOn", spentOn,
			"categoryId", categoryId,
			"merchant", merchant,
			"memo", memo,
			"imageIds", java.util.List.of()
		));
		mockMvc.perform(post("/api/v1/expenses")
				.with(csrf())
				.cookie(session)
				.contentType(MediaType.APPLICATION_JSON)
				.content(body))
			.andExpect(status().isCreated());
	}

	private Cookie sessionCookie(String setCookieHeader) {
		assertThat(setCookieHeader).isNotNull().contains("MBD_SESSION=");
		String prefix = "MBD_SESSION=";
		int valueStart = setCookieHeader.indexOf(prefix) + prefix.length();
		int valueEnd = setCookieHeader.indexOf(';', valueStart);
		return new Cookie("MBD_SESSION", setCookieHeader.substring(valueStart, valueEnd));
	}
}
