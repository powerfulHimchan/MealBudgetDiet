package com.mealbudgetdiet.expense.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;

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
@TestPropertySource(properties = {
	"app.bootstrap-token=expense-bootstrap-token",
	"app.public-base-url=https://budget.example"
})
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class ExpenseManagementIntegrationTest {

	@Autowired
	private MockMvc mockMvc;

	@Test
	void managesSharedExpensesAndAdminCategoriesWithConflictDetection() throws Exception {
		Cookie adminSession = bootstrapAdmin();
		Cookie memberSession = registerMember(adminSession);

		var categoriesResult = mockMvc.perform(get("/api/v1/categories").cookie(adminSession))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.items.length()").value(5))
			.andExpect(jsonPath("$.items[0].name").value("장보기"))
			.andReturn();
		String defaultCategoryId = JsonPath.read(
			categoriesResult.getResponse().getContentAsString(), "$.items[0].id");

		mockMvc.perform(post("/api/v1/categories")
				.with(csrf())
				.cookie(memberSession)
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"name\":\"회사 점심\",\"sortOrder\":6}"))
			.andExpect(status().isForbidden())
			.andExpect(jsonPath("$.code").value("ADMIN_REQUIRED"));

		var customCategoryResult = mockMvc.perform(post("/api/v1/categories")
				.with(csrf())
				.cookie(adminSession)
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"name\":\"회사 점심\",\"sortOrder\":6}"))
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.version").value(0))
			.andReturn();
		String customCategoryId = JsonPath.read(
			customCategoryResult.getResponse().getContentAsString(), "$.id");

		String firstExpenseId = createExpense(
			memberSession, 18_500, "2026-09-08", defaultCategoryId, "동네마트", "주말 장보기");
		createExpense(memberSession, 9_800, "2026-09-07", defaultCategoryId, "커피하우스", "간식");
		String customCategoryExpenseId = createExpense(
			adminSession, 12_000, "2026-09-06", customCategoryId, "구내식당", "점심");

		var firstPage = mockMvc.perform(get("/api/v1/expenses")
				.cookie(memberSession)
				.param("from", "2026-09-01")
				.param("to", "2026-09-30")
				.param("size", "2"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.items.length()").value(2))
			.andExpect(jsonPath("$.items[0].merchant").value("동네마트"))
			.andExpect(jsonPath("$.hasNext").value(true))
			.andReturn();
		String cursor = JsonPath.read(firstPage.getResponse().getContentAsString(), "$.nextCursor");

		mockMvc.perform(get("/api/v1/expenses")
				.cookie(memberSession)
				.param("size", "2")
				.param("cursor", cursor))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.items.length()").value(1))
			.andExpect(jsonPath("$.items[0].merchant").value("구내식당"))
			.andExpect(jsonPath("$.hasNext").value(false));

		mockMvc.perform(get("/api/v1/expenses")
				.cookie(adminSession)
				.param("keyword", "마트"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.items.length()").value(1))
			.andExpect(jsonPath("$.items[0].id").value(firstExpenseId));

		mockMvc.perform(put("/api/v1/expenses/{expenseId}", firstExpenseId)
				.with(csrf())
				.cookie(adminSession)
				.contentType(MediaType.APPLICATION_JSON)
				.content(expenseBody(20_000, "2026-09-08", defaultCategoryId, "동네마트", "수정", 0)))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.amount").value(20_000))
			.andExpect(jsonPath("$.version").value(1));

		mockMvc.perform(put("/api/v1/expenses/{expenseId}", firstExpenseId)
				.with(csrf())
				.cookie(memberSession)
				.contentType(MediaType.APPLICATION_JSON)
				.content(expenseBody(21_000, "2026-09-08", defaultCategoryId, "동네마트", "충돌", 0)))
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("EXPENSE_VERSION_CONFLICT"));

		mockMvc.perform(delete("/api/v1/categories/{categoryId}", customCategoryId)
				.with(csrf())
				.cookie(adminSession)
				.param("version", "0"))
			.andExpect(status().isNoContent());

		mockMvc.perform(get("/api/v1/expenses/{expenseId}", customCategoryExpenseId).cookie(memberSession))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.category").doesNotExist());

		mockMvc.perform(delete("/api/v1/expenses/{expenseId}", firstExpenseId)
				.with(csrf())
				.cookie(memberSession)
				.param("version", "0"))
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("EXPENSE_VERSION_CONFLICT"));

		mockMvc.perform(delete("/api/v1/expenses/{expenseId}", firstExpenseId)
				.with(csrf())
				.cookie(memberSession)
				.param("version", "1"))
			.andExpect(status().isNoContent());

		mockMvc.perform(get("/api/v1/expenses/{expenseId}", firstExpenseId).cookie(adminSession))
			.andExpect(status().isNotFound())
			.andExpect(jsonPath("$.code").value("EXPENSE_NOT_FOUND"));

		mockMvc.perform(get("/api/v1/expenses")
				.cookie(adminSession)
				.param("cursor", "not-a-cursor"))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("INVALID_EXPENSE_CURSOR"));
	}

	private Cookie bootstrapAdmin() throws Exception {
		var result = mockMvc.perform(post("/api/v1/bootstrap/admin")
				.with(csrf())
				.header("X-Bootstrap-Token", "expense-bootstrap-token")
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{
					  "email":"owner-expense@example.com",
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
		var invitationResult = mockMvc.perform(post("/api/v1/invitations")
				.with(csrf())
				.cookie(adminSession))
			.andExpect(status().isCreated())
			.andReturn();
		String invitationCode = JsonPath.read(invitationResult.getResponse().getContentAsString(), "$.code");

		var result = mockMvc.perform(post("/api/v1/auth/register")
				.with(csrf())
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{
					  "inviteCode":"%s",
					  "email":"member-expense@example.com",
					  "password":"member-password!",
					  "displayName":"가족 구성원"
					}
					""".formatted(invitationCode)))
			.andExpect(status().isCreated())
			.andReturn();
		return sessionCookie(result.getResponse().getHeader("Set-Cookie"));
	}

	private String createExpense(
		Cookie session,
		long amount,
		String spentOn,
		String categoryId,
		String merchant,
		String memo
	) throws Exception {
		var result = mockMvc.perform(post("/api/v1/expenses")
				.with(csrf())
				.cookie(session)
				.contentType(MediaType.APPLICATION_JSON)
				.content(expenseBody(amount, spentOn, categoryId, merchant, memo, null)))
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.category.id").value(categoryId))
			.andReturn();
		return JsonPath.read(result.getResponse().getContentAsString(), "$.id");
	}

	private String expenseBody(
		long amount,
		String spentOn,
		String categoryId,
		String merchant,
		String memo,
		Integer version
	) {
		return """
			{
			  "amount":%d,
			  "spentOn":"%s",
			  "categoryId":"%s",
			  "merchant":"%s",
			  "memo":"%s",
			  "imageIds":[]%s
			}
			""".formatted(
			amount,
			spentOn,
			categoryId,
			merchant,
			memo,
			version == null ? "" : ",\n  \"version\":" + version
		);
	}

	private Cookie sessionCookie(String setCookieHeader) {
		assertThat(setCookieHeader).isNotNull().contains("MBD_SESSION=");
		String prefix = "MBD_SESSION=";
		int valueStart = setCookieHeader.indexOf(prefix) + prefix.length();
		int valueEnd = setCookieHeader.indexOf(';', valueStart);
		return new Cookie("MBD_SESSION", setCookieHeader.substring(valueStart, valueEnd));
	}
}
