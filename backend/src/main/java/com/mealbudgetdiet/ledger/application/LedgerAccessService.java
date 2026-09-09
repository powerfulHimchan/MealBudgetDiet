package com.mealbudgetdiet.ledger.application;

import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.UUID;
import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.mealbudgetdiet.identity.application.IdentityService;
import com.mealbudgetdiet.identity.domain.User;
import com.mealbudgetdiet.ledger.domain.Ledger;
import com.mealbudgetdiet.ledger.domain.LedgerMember;
import com.mealbudgetdiet.ledger.domain.MemberStatus;
import com.mealbudgetdiet.ledger.infrastructure.LedgerMemberRepository;
import com.mealbudgetdiet.ledger.infrastructure.LedgerRepository;
import com.mealbudgetdiet.shared.api.ApiException;

@Service
public class LedgerAccessService {

	private final LedgerRepository ledgerRepository;
	private final LedgerMemberRepository memberRepository;
	private final IdentityService identityService;

	public LedgerAccessService(
		LedgerRepository ledgerRepository,
		LedgerMemberRepository memberRepository,
		IdentityService identityService
	) {
		this.ledgerRepository = ledgerRepository;
		this.memberRepository = memberRepository;
		this.identityService = identityService;
	}

	@Transactional(readOnly = true)
	public LedgerSnapshot getLedger(UUID userId) {
		var membership = requireActiveMembership(userId);
		var ledger = requireLedger(membership.getId().getLedgerId());
		long memberCount = memberRepository.countByIdLedgerIdAndStatus(ledger.getId(), MemberStatus.ACTIVE);
		return new LedgerSnapshot(
			ledger.getId(),
			ledger.getName(),
			ledger.getDefaultMonthlyBudget(),
			ledger.getBudgetCycleStartDay(),
			ledger.getPushUsageThreshold(),
			memberCount,
			membership.getRole(),
			ledger.getVersion()
		);
	}

	@Transactional(readOnly = true)
	public List<MemberSnapshot> getMembers(UUID userId) {
		var membership = requireActiveMembership(userId);
		var members = memberRepository.findAllActiveByLedgerId(membership.getId().getLedgerId());
		var usersById = identityService.getUsers(
			members.stream().map(member -> member.getId().getUserId()).toList()
		).stream().collect(Collectors.toMap(User::getId, Function.identity()));

		return members.stream().map(member -> {
			var user = usersById.get(member.getId().getUserId());
			return new MemberSnapshot(
				user.getId(), user.getDisplayName(), member.getRole(), member.getJoinedAt(),
				user.getProfileImageId() == null ? null : "/api/v1/images/" + user.getProfileImageId() + "/content"
			);
		}).toList();
	}

	public LedgerMember requireActiveMembership(UUID userId) {
		return memberRepository.findActiveByUserId(userId)
			.orElseThrow(() -> new ApiException(HttpStatus.FORBIDDEN, "MEMBERSHIP_REQUIRED", "장부 참여 권한이 없습니다."));
	}

	public Ledger requireLedger(UUID ledgerId) {
		return ledgerRepository.findById(ledgerId)
			.orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "LEDGER_NOT_FOUND", "장부를 찾을 수 없습니다."));
	}
}
