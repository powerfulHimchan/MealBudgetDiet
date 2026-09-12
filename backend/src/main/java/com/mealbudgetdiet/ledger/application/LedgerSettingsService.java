package com.mealbudgetdiet.ledger.application;

import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.mealbudgetdiet.ledger.domain.MemberRole;
import com.mealbudgetdiet.budget.domain.BudgetCycleUnit;
import com.mealbudgetdiet.ledger.infrastructure.LedgerRepository;
import com.mealbudgetdiet.shared.api.ApiException;

@Service
public class LedgerSettingsService {

	private final LedgerAccessService ledgerAccessService;
	private final LedgerRepository ledgerRepository;

	public LedgerSettingsService(LedgerAccessService ledgerAccessService, LedgerRepository ledgerRepository) {
		this.ledgerAccessService = ledgerAccessService;
		this.ledgerRepository = ledgerRepository;
	}

	@Transactional
	public LedgerSettingsSnapshot updateBudgetCycle(UUID userId, BudgetCycleUnit unit, int startDay,
		int weekStartDay, Long weeklyBudget, int version) {
		var membership = ledgerAccessService.requireActiveMembership(userId);
		if (membership.getRole() != MemberRole.ADMIN) {
			throw new ApiException(HttpStatus.FORBIDDEN, "ADMIN_REQUIRED", "관리자 권한이 필요합니다.");
		}
		var ledger = ledgerAccessService.requireLedger(membership.getId().getLedgerId());
		if (ledger.getVersion() != version) {
			throw new ApiException(
				HttpStatus.CONFLICT,
				"LEDGER_VERSION_CONFLICT",
				"다른 사용자가 장부 설정을 먼저 변경했습니다."
			);
		}
		Long effectiveWeeklyBudget = weeklyBudget == null ? ledger.getDefaultWeeklyBudget() : weeklyBudget;
		if (unit == BudgetCycleUnit.WEEKLY && effectiveWeeklyBudget == null) {
			throw new ApiException(HttpStatus.BAD_REQUEST, "WEEKLY_BUDGET_REQUIRED", "주간 예산을 입력해 주세요.");
		}
		ledger.changeBudgetCycle(unit, startDay, weekStartDay, effectiveWeeklyBudget);
		ledgerRepository.flush();
		return new LedgerSettingsSnapshot(ledger.getBudgetCycleUnit(), ledger.getBudgetCycleStartDay(),
			ledger.getBudgetWeekStartDay(), ledger.getDefaultWeeklyBudget(), ledger.getVersion());
	}

	@Transactional
	public PushThresholdSettingsSnapshot updatePushThreshold(UUID userId, int usageThreshold, int version) {
		var membership = ledgerAccessService.requireActiveMembership(userId);
		if (membership.getRole() != MemberRole.ADMIN) {
			throw new ApiException(HttpStatus.FORBIDDEN, "ADMIN_REQUIRED", "관리자 권한이 필요합니다.");
		}
		var ledger = ledgerAccessService.requireLedger(membership.getId().getLedgerId());
		if (ledger.getVersion() != version) {
			throw new ApiException(
				HttpStatus.CONFLICT,
				"LEDGER_VERSION_CONFLICT",
				"다른 사용자가 장부 설정을 먼저 변경했습니다."
			);
		}
		ledger.changePushUsageThreshold(usageThreshold);
		ledgerRepository.flush();
		return new PushThresholdSettingsSnapshot(ledger.getPushUsageThreshold(), ledger.getVersion());
	}
}
