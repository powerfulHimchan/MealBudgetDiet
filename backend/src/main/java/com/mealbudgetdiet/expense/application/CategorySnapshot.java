package com.mealbudgetdiet.expense.application;

import java.util.UUID;

public record CategorySnapshot(UUID id, String name, int sortOrder, int version) {
}
