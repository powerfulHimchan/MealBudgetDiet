package com.mealbudgetdiet.media.domain;

import java.io.Serializable;
import java.util.UUID;

public record ExpenseImageId(UUID expenseId, UUID imageId) implements Serializable {
}
