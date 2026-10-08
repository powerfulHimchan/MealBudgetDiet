package com.mealbudgetdiet.ai.application;

public record AiTextResult(String model, String text, int inputTokens, int outputTokens) {
}
