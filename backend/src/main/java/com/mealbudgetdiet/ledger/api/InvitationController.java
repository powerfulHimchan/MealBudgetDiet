package com.mealbudgetdiet.ledger.api;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.mealbudgetdiet.identity.infrastructure.MealBudgetPrincipal;
import com.mealbudgetdiet.ledger.application.CreatedInvitation;
import com.mealbudgetdiet.ledger.application.InvitationService;
import com.mealbudgetdiet.ledger.application.InvitationSnapshot;
import com.mealbudgetdiet.ledger.application.InvitationStatus;

@RestController
@RequestMapping("/api/v1/invitations")
public class InvitationController {

	private static final ZoneId SERVICE_ZONE = ZoneId.of("Asia/Seoul");

	private final InvitationService invitationService;

	public InvitationController(InvitationService invitationService) {
		this.invitationService = invitationService;
	}

	@GetMapping
	InvitationsResponse invitations(
		@AuthenticationPrincipal MealBudgetPrincipal principal,
		@RequestParam(defaultValue = "ACTIVE") InvitationStatus status
	) {
		return new InvitationsResponse(invitationService.getInvitations(principal.id(), status).stream()
			.map(InvitationResponse::from)
			.toList());
	}

	@PostMapping
	ResponseEntity<CreatedInvitationResponse> create(
		@AuthenticationPrincipal MealBudgetPrincipal principal
	) {
		return ResponseEntity.status(HttpStatus.CREATED)
			.body(CreatedInvitationResponse.from(invitationService.createInvitation(principal.id())));
	}

	@DeleteMapping("/{invitationId}")
	ResponseEntity<Void> revoke(
		@AuthenticationPrincipal MealBudgetPrincipal principal,
		@PathVariable UUID invitationId
	) {
		invitationService.revokeInvitation(principal.id(), invitationId);
		return ResponseEntity.noContent().build();
	}

	private record InvitationsResponse(List<InvitationResponse> items) {
	}

	private record InvitationResponse(
		UUID id,
		String maskedCode,
		InvitationStatus status,
		long useCount,
		OffsetDateTime createdAt,
		OffsetDateTime lastUsedAt
	) {
		static InvitationResponse from(InvitationSnapshot snapshot) {
			return new InvitationResponse(
				snapshot.id(),
				snapshot.maskedCode(),
				snapshot.status(),
				snapshot.useCount(),
				toServiceTime(snapshot.createdAt()),
				toServiceTime(snapshot.lastUsedAt())
			);
		}
	}

	private record CreatedInvitationResponse(UUID id, String code, String joinUrl, OffsetDateTime createdAt) {
		static CreatedInvitationResponse from(CreatedInvitation invitation) {
			return new CreatedInvitationResponse(
				invitation.id(), invitation.code(), invitation.joinUrl(), toServiceTime(invitation.createdAt()));
		}
	}

	private static OffsetDateTime toServiceTime(Instant instant) {
		return instant == null ? null : instant.atZone(SERVICE_ZONE).toOffsetDateTime();
	}
}
