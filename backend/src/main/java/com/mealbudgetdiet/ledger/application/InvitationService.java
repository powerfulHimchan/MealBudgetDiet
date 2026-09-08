package com.mealbudgetdiet.ledger.application;

import java.security.SecureRandom;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.mealbudgetdiet.ledger.domain.Invitation;
import com.mealbudgetdiet.ledger.infrastructure.InvitationRepository;
import com.mealbudgetdiet.shared.api.ApiException;
import com.mealbudgetdiet.shared.security.TokenHasher;

@Service
public class InvitationService {

	private final InvitationRepository invitationRepository;
	private final LedgerAccessService ledgerAccessService;
	private final TokenHasher tokenHasher;
	private final java.time.Clock clock;
	private final SecureRandom secureRandom = new SecureRandom();
	private final String publicBaseUrl;

	public InvitationService(
		InvitationRepository invitationRepository,
		LedgerAccessService ledgerAccessService,
		TokenHasher tokenHasher,
		java.time.Clock clock,
		@Value("${app.public-base-url:http://localhost:3000}") String publicBaseUrl
	) {
		this.invitationRepository = invitationRepository;
		this.ledgerAccessService = ledgerAccessService;
		this.tokenHasher = tokenHasher;
		this.clock = clock;
		this.publicBaseUrl = publicBaseUrl.replaceAll("/+$", "");
	}

	@Transactional(readOnly = true)
	public List<InvitationSnapshot> getInvitations(UUID userId, InvitationStatus filter) {
		var membership = ledgerAccessService.requireActiveMembership(userId);
		return invitationRepository.findAllByLedgerIdOrderByCreatedAtDesc(membership.getId().getLedgerId())
			.stream()
			.filter(invitation -> filter == InvitationStatus.ALL
				|| filter == statusOf(invitation))
			.map(this::snapshot)
			.toList();
	}

	@Transactional
	public CreatedInvitation createInvitation(UUID userId) {
		var membership = ledgerAccessService.requireActiveMembership(userId);
		String rawCode = generateCode();
		String compactCode = rawCode.replace("-", "");
		String suffix = compactCode.substring(compactCode.length() - 4);
		var invitation = invitationRepository.saveAndFlush(new Invitation(
			membership.getId().getLedgerId(),
			userId,
			tokenHasher.hash(rawCode),
			suffix
		));
		return new CreatedInvitation(
			invitation.getId(),
			rawCode,
			publicBaseUrl + "/join?code=" + rawCode,
			invitation.getCreatedAt()
		);
	}

	@Transactional
	public void revokeInvitation(UUID userId, UUID invitationId) {
		var membership = ledgerAccessService.requireActiveMembership(userId);
		var invitation = invitationRepository.findByIdAndLedgerIdForUpdate(
			invitationId, membership.getId().getLedgerId())
			.orElseThrow(() -> new ApiException(
				HttpStatus.NOT_FOUND,
				"INVITATION_NOT_FOUND",
				"초대 코드를 찾을 수 없습니다."
			));
		invitation.revoke(clock.instant());
	}

	private InvitationSnapshot snapshot(Invitation invitation) {
		return new InvitationSnapshot(
			invitation.getId(),
			"MBD-****" + invitation.getCodeSuffix(),
			statusOf(invitation),
			invitation.getUseCount(),
			invitation.getCreatedAt(),
			invitation.getLastUsedAt()
		);
	}

	private InvitationStatus statusOf(Invitation invitation) {
		return invitation.isRevoked() ? InvitationStatus.REVOKED : InvitationStatus.ACTIVE;
	}

	private String generateCode() {
		byte[] randomBytes = new byte[12];
		secureRandom.nextBytes(randomBytes);
		String token = HexFormat.of().withUpperCase().formatHex(randomBytes);
		return "MBD-" + token.substring(0, 8) + "-" + token.substring(8, 16) + "-" + token.substring(16, 24);
	}
}
