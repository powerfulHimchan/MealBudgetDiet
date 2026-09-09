package com.mealbudgetdiet.expense.application;

import java.util.List;

public record ExpensePage(List<ExpenseSnapshot> items, String nextCursor, boolean hasNext) {
}
