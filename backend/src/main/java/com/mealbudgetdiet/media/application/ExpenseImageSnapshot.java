package com.mealbudgetdiet.media.application;

import java.util.UUID;

public record ExpenseImageSnapshot(UUID id, String contentUrl, int sortOrder) {
}
