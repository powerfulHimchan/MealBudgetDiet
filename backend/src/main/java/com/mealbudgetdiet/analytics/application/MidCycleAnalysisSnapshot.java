package com.mealbudgetdiet.analytics.application;

import java.time.Instant;
import java.time.LocalDate;

public record MidCycleAnalysisSnapshot(boolean eligible, String status, String text,
	LocalDate midpoint, LocalDate through, Instant analyzedAt) {
}
