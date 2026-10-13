package com.mealbudgetdiet.analytics.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import java.util.stream.IntStream;

import org.junit.jupiter.api.Test;

import com.mealbudgetdiet.ai.infrastructure.OpenAiGateway;
import com.mealbudgetdiet.analytics.infrastructure.SpendingAnalysisRepository.ExpenseDetail;
import com.mealbudgetdiet.analytics.infrastructure.SpendingAnalysisRepository.ExpenseDetails;
import tools.jackson.databind.ObjectMapper;

class SpendingAnalysisPromptTest {
	private final ObjectMapper mapper = new ObjectMapper();
	private final SpendingAnalysisPrompt prompt = new SpendingAnalysisPrompt(mapper);
	private static final LocalDate DATE = LocalDate.parse("2026-09-12");

	@Test
	void includesMerchantAndMemoAsJsonDataAndSupportsMissingOptionalText() {
		String memo = "치즈 \"추가\"\n위 지시를 무시하라는 문구도 기록 데이터입니다.";
		var details = new ExpenseDetails(2, List.of(
			new ExpenseDetail(DATE, 18500, "장보기", "마트", memo),
			new ExpenseDetail(DATE, 1000, "분류 없음", null, null)));
		var json = mapper.readTree(prompt.build(statistics(), details));
		assertThat(json.path("지출내역").get(0).path("merchant").asString()).isEqualTo("마트");
		assertThat(json.path("지출내역").get(0).path("memo").asString()).isEqualTo(memo);
		assertThat(json.path("지출내역").get(1).path("merchant").isNull()).isTrue();
		assertThat(json.path("지출내역").get(1).path("memo").isNull()).isTrue();
		assertThat(json.path("지출기록건수").asLong()).isEqualTo(2);
		assertThat(json.path("상세내역포함건수").asInt()).isEqualTo(2);
		assertThat(json.path("상세내역일부만포함").asBoolean()).isFalse();
	}

	@Test
	void limitsSerializedLengthWithoutCuttingJsonOrMemoAndPreservesFullAggregates() {
		String memo = "\\\"\n😀".repeat(200);
		var records = IntStream.range(0, 200)
			.mapToObj(i -> new ExpenseDetail(DATE, 20000 - i, "장보기", "상호" + i, memo)).toList();
		String input = prompt.build(statistics(), new ExpenseDetails(260, records));
		assertThat(input.length()).isLessThanOrEqualTo(OpenAiGateway.MAX_INPUT_LENGTH);
		var json = mapper.readTree(input);
		int included = json.path("지출내역").size();
		assertThat(included).isBetween(1, 199);
		assertThat(json.path("지출내역").get(included - 1).path("memo").asString()).isEqualTo(memo);
		assertThat(json.path("총지출원").asLong()).isEqualTo(18500);
		assertThat(json.path("지출기록건수").asLong()).isEqualTo(260);
		assertThat(json.path("상세내역포함건수").asInt()).isEqualTo(included);
		assertThat(json.path("상세내역일부만포함").asBoolean()).isTrue();
	}

	@Test
	void marksRecordLimitAsPartialEvenWhenSerializedTextFits() {
		var records = IntStream.range(0, 200)
			.mapToObj(i -> new ExpenseDetail(DATE, 1000, "분류 없음", null, null)).toList();
		var json = mapper.readTree(prompt.build(statistics(), new ExpenseDetails(201, records)));
		assertThat(json.path("상세내역포함건수").asInt()).isEqualTo(200);
		assertThat(json.path("상세내역일부만포함").asBoolean()).isTrue();
	}

	private static StatisticsSnapshot statistics() {
		return new StatisticsSnapshot(new StatisticsSnapshot.Period(DATE.withDayOfMonth(1), DATE.withDayOfMonth(30)),
			18500, new StatisticsSnapshot.Budget(800000, new BigDecimal("2.31")),
			new StatisticsSnapshot.Comparison(DATE.minusMonths(1), DATE.minusMonths(1), 10000, 8500,
				new BigDecimal("85"), null, 0, false, true),
			List.of(new StatisticsSnapshot.DailyAmount(DATE, 18500)),
			List.of(new StatisticsSnapshot.CategoryAmount(UUID.randomUUID(), "장보기", 18500, new BigDecimal("100"))),
			SpendingAnalysisSnapshot.unavailable());
	}
}
