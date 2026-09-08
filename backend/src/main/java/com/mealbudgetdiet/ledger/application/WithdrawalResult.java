package com.mealbudgetdiet.ledger.application;

import java.util.List;

public record WithdrawalResult(boolean ledgerDeleted, List<String> invalidatedEmails) {
}
