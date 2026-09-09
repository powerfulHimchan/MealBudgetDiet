package com.mealbudgetdiet.ledger.application;

import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.mealbudgetdiet.ledger.domain.MemberRole;
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
	public LedgerSettingsSnapshot updateBudgetCycle(UUID userId, int startDay, int version) {
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
		ledger.changeBudgetCycleStartDay(startDay);
		ledgerRepository.flush();
		return new LedgerSettingsSnapshot(ledger.getBudgetCycleStartDay(), ledger.getVersion());
	}
}
