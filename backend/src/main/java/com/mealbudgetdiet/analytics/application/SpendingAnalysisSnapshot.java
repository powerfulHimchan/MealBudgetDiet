package com.mealbudgetdiet.analytics.application;

import java.time.Instant;

public record SpendingAnalysisSnapshot(boolean eligible, String status, String text, Instant analyzedAt) {
	public static SpendingAnalysisSnapshot unavailable() {
		return new SpendingAnalysisSnapshot(false, "UNAVAILABLE", null, null);
	}
	public static SpendingAnalysisSnapshot noExpenses() {
		return new SpendingAnalysisSnapshot(false, "NO_EXPENSES", null, null);
	}
}
