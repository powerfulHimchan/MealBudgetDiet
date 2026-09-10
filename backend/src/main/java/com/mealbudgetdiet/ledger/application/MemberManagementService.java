package com.mealbudgetdiet.ledger.application;

import java.time.Clock;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.mealbudgetdiet.identity.application.IdentityService;
import com.mealbudgetdiet.identity.domain.User;
import com.mealbudgetdiet.ledger.domain.LedgerMemberId;
import com.mealbudgetdiet.ledger.domain.MemberRole;
import com.mealbudgetdiet.ledger.domain.MemberStatus;
import com.mealbudgetdiet.ledger.infrastructure.LedgerMemberRepository;
import com.mealbudgetdiet.ledger.infrastructure.LedgerRepository;
import com.mealbudgetdiet.media.application.ImageService;
import com.mealbudgetdiet.shared.api.ApiException;

@Service
public class MemberManagementService {

	private final LedgerAccessService ledgerAccessService;
	private final LedgerRepository ledgerRepository;
	private final LedgerMemberRepository memberRepository;
	private final IdentityService identityService;
	private final ImageService imageService;
	private final Clock clock;

	public MemberManagementService(
		LedgerAccessService ledgerAccessService,
		LedgerRepository ledgerRepository,
		LedgerMemberRepository memberRepository,
		IdentityService identityService,
		ImageService imageService,
		Clock clock
	) {
		this.ledgerAccessService = ledgerAccessService;
		this.ledgerRepository = ledgerRepository;
		this.memberRepository = memberRepository;
		this.identityService = identityService;
		this.imageService = imageService;
		this.clock = clock;
	}

	@Transactional
	public MemberSnapshot changeRole(UUID actorUserId, UUID targetUserId, MemberRole newRole) {
		var currentMembership = ledgerAccessService.requireActiveMembership(actorUserId);
		UUID ledgerId = currentMembership.getId().getLedgerId();
		ledgerRepository.findByIdForUpdate(ledgerId)
			.orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "LEDGER_NOT_FOUND", "장부를 찾을 수 없습니다."));

		var actor = memberRepository.findByLedgerAndUserForUpdate(ledgerId, actorUserId)
			.orElseThrow(() -> new ApiException(HttpStatus.FORBIDDEN, "MEMBERSHIP_REQUIRED", "장부 참여 권한이 없습니다."));
		if (actor.getStatus() != MemberStatus.ACTIVE || actor.getRole() != MemberRole.ADMIN) {
			throw new ApiException(HttpStatus.FORBIDDEN, "ADMIN_REQUIRED", "관리자 권한이 필요합니다.");
		}

		var target = memberRepository.findByLedgerAndUserForUpdate(ledgerId, targetUserId)
			.orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "MEMBER_NOT_FOUND", "참여자를 찾을 수 없습니다."));
		if (target.getStatus() != MemberStatus.ACTIVE) {
			throw new ApiException(HttpStatus.NOT_FOUND, "MEMBER_NOT_FOUND", "참여자를 찾을 수 없습니다.");
		}
		if (target.getRole() == MemberRole.ADMIN && newRole == MemberRole.MEMBER
			&& memberRepository.countByIdLedgerIdAndStatusAndRole(
				ledgerId, MemberStatus.ACTIVE, MemberRole.ADMIN) <= 1) {
			throw new ApiException(HttpStatus.CONFLICT, "LAST_ADMIN_REQUIRED", "마지막 관리자는 해제할 수 없습니다.");
		}

		target.changeRole(newRole);
		var user = identityService.getUser(targetUserId);
		return new MemberSnapshot(
			user.getId(), user.getDisplayName(), target.getRole(), target.getJoinedAt(),
			user.getProfileImageId() == null ? null : ImageService.contentUrl(user.getProfileImageId()));
	}

	@Transactional
	public WithdrawalResult withdraw(UUID userId, String password, String confirmation) {
		var currentMembership = ledgerAccessService.requireActiveMembership(userId);
		UUID ledgerId = currentMembership.getId().getLedgerId();
		var ledger = ledgerRepository.findByIdForUpdate(ledgerId)
			.orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "LEDGER_NOT_FOUND", "장부를 찾을 수 없습니다."));
		var membership = memberRepository.findByLedgerAndUserForUpdate(ledgerId, userId)
			.orElseThrow(() -> new ApiException(HttpStatus.FORBIDDEN, "MEMBERSHIP_REQUIRED", "장부 참여 권한이 없습니다."));
		var user = identityService.getUser(userId);
		if (!identityService.passwordMatches(user, password)) {
			throw new ApiException(HttpStatus.UNAUTHORIZED, "INVALID_CREDENTIALS", "비밀번호가 올바르지 않습니다.");
		}

		long adminCount = memberRepository.countByIdLedgerIdAndStatusAndRole(
			ledgerId, MemberStatus.ACTIVE, MemberRole.ADMIN);
		if (membership.getRole() == MemberRole.ADMIN && adminCount == 1) {
			String expectedConfirmation = ledger.getName() + " 삭제";
			if (!expectedConfirmation.equals(confirmation)) {
				throw new ApiException(
					HttpStatus.BAD_REQUEST,
					"LEDGER_DELETION_CONFIRMATION_REQUIRED",
					"장부 종료 확인 문구가 일치하지 않습니다."
				);
			}

			var allMemberships = memberRepository.findAllByIdLedgerId(ledgerId);
			var usersById = identityService.getUsers(
				allMemberships.stream().map(item -> item.getId().getUserId()).toList()
			).stream().collect(Collectors.toMap(User::getId, Function.identity()));
			var users = allMemberships.stream()
				.map(item -> usersById.get(item.getId().getUserId()))
				.filter(java.util.Objects::nonNull)
				.distinct()
				.toList();
			var emails = users.stream().map(User::getEmail).toList();

			imageService.deleteLedgerObjectsAfterCommit(ledgerId);
			ledgerRepository.delete(ledger);
			ledgerRepository.flush();
			identityService.deleteUsers(users);
			return new WithdrawalResult(true, emails);
		}

		imageService.deleteProfileImage(userId);
		membership.leave(clock.instant());
		String invalidatedEmail = user.getEmail();
		identityService.withdraw(user);
		return new WithdrawalResult(false, java.util.List.of(invalidatedEmail));
	}
}
