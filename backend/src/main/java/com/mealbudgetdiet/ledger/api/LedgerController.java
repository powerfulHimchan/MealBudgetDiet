package com.mealbudgetdiet.ledger.api;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.logout.SecurityContextLogoutHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.mealbudgetdiet.identity.application.SessionInvalidationService;
import com.mealbudgetdiet.identity.infrastructure.MealBudgetPrincipal;
import com.mealbudgetdiet.ledger.application.LedgerAccessService;
import com.mealbudgetdiet.ledger.application.LedgerSnapshot;
import com.mealbudgetdiet.ledger.application.LedgerSettingsService;
import com.mealbudgetdiet.ledger.application.MemberManagementService;
import com.mealbudgetdiet.ledger.application.MemberSnapshot;
import com.mealbudgetdiet.ledger.domain.MemberRole;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

@RestController
@RequestMapping("/api/v1")
public class LedgerController {

	private static final ZoneId SERVICE_ZONE = ZoneId.of("Asia/Seoul");

	private final LedgerAccessService ledgerAccessService;
	private final MemberManagementService memberManagementService;
	private final SessionInvalidationService sessionInvalidationService;
	private final LedgerSettingsService ledgerSettingsService;

	public LedgerController(
		LedgerAccessService ledgerAccessService,
		MemberManagementService memberManagementService,
		SessionInvalidationService sessionInvalidationService,
		LedgerSettingsService ledgerSettingsService
	) {
		this.ledgerAccessService = ledgerAccessService;
		this.memberManagementService = memberManagementService;
		this.sessionInvalidationService = sessionInvalidationService;
		this.ledgerSettingsService = ledgerSettingsService;
	}

	@GetMapping("/ledger")
	LedgerResponse ledger(@AuthenticationPrincipal MealBudgetPrincipal principal) {
		return LedgerResponse.from(ledgerAccessService.getLedger(principal.id()));
	}

	@GetMapping("/members")
	MembersResponse members(@AuthenticationPrincipal MealBudgetPrincipal principal) {
		return new MembersResponse(ledgerAccessService.getMembers(principal.id()).stream()
			.map(MemberResponse::from)
			.toList());
	}

	@PutMapping("/ledger/settings/budget-cycle")
	LedgerSettingsResponse updateBudgetCycle(
		@AuthenticationPrincipal MealBudgetPrincipal principal,
		@Valid @RequestBody BudgetCycleRequest request
	) {
		var snapshot = ledgerSettingsService.updateBudgetCycle(
			principal.id(), request.startDay(), request.version());
		return new LedgerSettingsResponse(snapshot.budgetCycleStartDay(), snapshot.version());
	}

	@PutMapping("/ledger/settings/push-threshold")
	PushThresholdSettingsResponse updatePushThreshold(
		@AuthenticationPrincipal MealBudgetPrincipal principal,
		@Valid @RequestBody PushThresholdRequest request
	) {
		var snapshot = ledgerSettingsService.updatePushThreshold(
			principal.id(), request.usageThreshold(), request.version());
		return new PushThresholdSettingsResponse(snapshot.pushUsageThreshold(), snapshot.version());
	}

	@PatchMapping("/members/{memberId}/role")
	MemberResponse changeRole(
		@AuthenticationPrincipal MealBudgetPrincipal principal,
		@PathVariable UUID memberId,
		@Valid @RequestBody ChangeRoleRequest request
	) {
		return MemberResponse.from(memberManagementService.changeRole(principal.id(), memberId, request.role()));
	}

	@PostMapping("/account/withdrawal")
	ResponseEntity<Void> withdraw(
		@AuthenticationPrincipal MealBudgetPrincipal principal,
		@Valid @RequestBody WithdrawalRequest requestBody,
		HttpServletRequest request,
		HttpServletResponse response
	) {
		var result = memberManagementService.withdraw(
			principal.id(), requestBody.password(), requestBody.confirmation());
		sessionInvalidationService.invalidateByEmails(result.invalidatedEmails());
		new SecurityContextLogoutHandler().logout(
			request, response, SecurityContextHolder.getContext().getAuthentication());
		return ResponseEntity.noContent().build();
	}

	private record LedgerResponse(
		UUID id,
		String name,
		long defaultMonthlyBudget,
		int budgetCycleStartDay,
		int pushUsageThreshold,
		long memberCount,
		MemberRole currentUserRole,
		int version
	) {
		static LedgerResponse from(LedgerSnapshot snapshot) {
			return new LedgerResponse(
				snapshot.id(), snapshot.name(), snapshot.defaultMonthlyBudget(), snapshot.budgetCycleStartDay(),
				snapshot.pushUsageThreshold(), snapshot.memberCount(),
				snapshot.currentUserRole(), snapshot.version());
		}
	}

	private record MembersResponse(List<MemberResponse> items) {
	}

	private record MemberResponse(UUID id, String displayName, MemberRole role, OffsetDateTime joinedAt, String profileImageUrl) {
		static MemberResponse from(MemberSnapshot snapshot) {
			return new MemberResponse(
				snapshot.id(), snapshot.displayName(), snapshot.role(), toServiceTime(snapshot.joinedAt()),
				snapshot.profileImageUrl());
		}
	}

	public record ChangeRoleRequest(@NotNull(message = "역할을 선택해 주세요.") MemberRole role) {
	}

	public record BudgetCycleRequest(
		@NotNull(message = "예산 주기 시작일을 입력해 주세요.")
		@Min(value = 1, message = "예산 주기 시작일은 1일 이상이어야 합니다.")
		@Max(value = 31, message = "예산 주기 시작일은 31일 이하여야 합니다.")
		Integer startDay,
		@NotNull(message = "버전 정보가 필요합니다.")
		@PositiveOrZero(message = "버전 정보가 올바르지 않습니다.")
		Integer version
	) {
	}

	private record LedgerSettingsResponse(int budgetCycleStartDay, int version) {
	}

	public record PushThresholdRequest(
		@NotNull(message = "Push 기준 사용률을 입력해 주세요.")
		@Min(value = 1, message = "Push 기준 사용률은 1% 이상이어야 합니다.")
		@Max(value = 100, message = "Push 기준 사용률은 100% 이하여야 합니다.")
		Integer usageThreshold,
		@NotNull(message = "버전 정보가 필요합니다.")
		@PositiveOrZero(message = "버전 정보가 올바르지 않습니다.")
		Integer version
	) {
	}

	private record PushThresholdSettingsResponse(int pushUsageThreshold, int version) {
	}

	public record WithdrawalRequest(
		@NotBlank(message = "비밀번호를 입력해 주세요.")
		@Size(max = 72, message = "비밀번호는 72자 이하여야 합니다.")
		String password,
		String confirmation
	) {
	}

	private static OffsetDateTime toServiceTime(Instant instant) {
		return instant.atZone(SERVICE_ZONE).toOffsetDateTime();
	}
}
