package com.mealbudgetdiet.analytics.application;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.Map;

import com.mealbudgetdiet.ai.infrastructure.OpenAiGateway;
import com.mealbudgetdiet.analytics.infrastructure.SpendingAnalysisRepository.ExpenseDetail;
import com.mealbudgetdiet.analytics.infrastructure.SpendingAnalysisRepository.ExpenseDetails;
import tools.jackson.databind.ObjectMapper;

final class SpendingAnalysisPrompt {
	private final ObjectMapper mapper;

	SpendingAnalysisPrompt(ObjectMapper mapper) {
		this.mapper = mapper;
	}

	String build(StatisticsSnapshot statistics, ExpenseDetails details) {
		return build(statistics, details, Map.of());
	}

	String build(StatisticsSnapshot statistics, ExpenseDetails details, Map<String, Object> context) {
		Map<String, Object> data = new LinkedHashMap<>();
		data.putAll(context);
		data.put("기간", statistics.period());
		data.put("총지출원", statistics.totalAmount());
		data.put("예산원", statistics.budget().amount());
		if (statistics.budget().rollover() != null && statistics.budget().rollover().enabled()) {
			data.put("예산이월", statistics.budget().rollover());
		}
		data.put("예산사용률", statistics.budget().usageRate());
		data.put("이전주기지출원", statistics.comparison().totalAmount());
		data.put("이전주기대비증감률", statistics.comparison().changeRate());
		data.put("주요카테고리", statistics.categories().stream().limit(8).map(category -> Map.of(
			"분류", category.categoryName(), "지출원", category.amount(), "비중", category.ratio())).toList());
		data.put("지출기록일수", statistics.daily().size());
		statistics.daily().stream().max(Comparator.comparingLong(StatisticsSnapshot.DailyAmount::amount))
			.ifPresent(day -> data.put("최다지출일", day));
		data.put("지출기록건수", details.totalCount());
		var included = new ArrayList<ExpenseDetail>();
		data.put("지출내역", included);
		setCoverage(data, details.totalCount(), 0);
		for (var detail : details.items()) {
			included.add(detail);
			setCoverage(data, details.totalCount(), included.size());
			if (mapper.writeValueAsString(data).length() > OpenAiGateway.MAX_INPUT_LENGTH) {
				included.removeLast();
				break;
			}
		}
		setCoverage(data, details.totalCount(), included.size());
		return mapper.writeValueAsString(data);
	}

	private static void setCoverage(Map<String, Object> data, long totalCount, int includedCount) {
		data.put("상세내역포함건수", includedCount);
		data.put("상세내역일부만포함", includedCount < totalCount);
	}
}
