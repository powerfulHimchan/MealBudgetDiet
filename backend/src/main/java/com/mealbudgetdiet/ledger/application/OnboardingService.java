package com.mealbudgetdiet.ledger.application;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.mealbudgetdiet.identity.application.IdentityService;
import com.mealbudgetdiet.ledger.domain.Ledger;
import com.mealbudgetdiet.ledger.domain.LedgerMember;
import com.mealbudgetdiet.ledger.domain.MemberRole;
import com.mealbudgetdiet.ledger.infrastructure.InvitationRepository;
import com.mealbudgetdiet.ledger.infrastructure.LedgerMemberRepository;
import com.mealbudgetdiet.ledger.infrastructure.LedgerRepository;
import com.mealbudgetdiet.shared.api.ApiException;
import com.mealbudgetdiet.shared.security.TokenHasher;

@Service
public class OnboardingService {

	private static final long BOOTSTRAP_LOCK_KEY = 4_623_344_781L;

	private final IdentityService identityService;
	private final LedgerRepository ledgerRepository;
	private final LedgerMemberRepository memberRepository;
	private final InvitationRepository invitationRepository;
	private final TokenHasher tokenHasher;
	private final JdbcTemplate jdbcTemplate;
	private final Clock clock;
	private final String bootstrapToken;

	public OnboardingService(
		IdentityService identityService,
		LedgerRepository ledgerRepository,
		LedgerMemberRepository memberRepository,
		InvitationRepository invitationRepository,
		TokenHasher tokenHasher,
		JdbcTemplate jdbcTemplate,
		Clock clock,
		@Value("${app.bootstrap-token:}") String bootstrapToken
	) {
		this.identityService = identityService;
		this.ledgerRepository = ledgerRepository;
		this.memberRepository = memberRepository;
		this.invitationRepository = invitationRepository;
		this.tokenHasher = tokenHasher;
		this.jdbcTemplate = jdbcTemplate;
		this.clock = clock;
		this.bootstrapToken = bootstrapToken;
	}

	@Transactional(readOnly = true)
	public boolean isBootstrapAvailable() {
		return identityService.countUsers() == 0;
	}

	@Transactional
	public AuthenticatedUser bootstrapAdmin(
		String suppliedToken,
		String email,
		String password,
		String displayName,
		String ledgerName,
		long defaultMonthlyBudget
	) {
		verifyBootstrapToken(suppliedToken);
		jdbcTemplate.execute("select pg_advisory_xact_lock(" + BOOTSTRAP_LOCK_KEY + ")");
		if (identityService.countUsers() != 0) {
			throw new ApiException(HttpStatus.CONFLICT, "BOOTSTRAP_ALREADY_COMPLETED", "최초 관리자 설정이 이미 완료되었습니다.");
		}

		var user = identityService.createUser(email, password, displayName);
		var ledger = ledgerRepository.saveAndFlush(new Ledger(ledgerName.trim(), defaultMonthlyBudget));
		memberRepository.save(new LedgerMember(ledger.getId(), user.getId(), MemberRole.ADMIN));
		jdbcTemplate.queryForObject("select seed_default_categories(?)", Integer.class, ledger.getId());

		return new AuthenticatedUser(user.getId(), user.getEmail(), user.getDisplayName(), MemberRole.ADMIN);
	}

	@Transactional
	public AuthenticatedUser register(String inviteCode, String email, String password, String displayName) {
		var invitation = invitationRepository.findByTokenHashForUpdate(tokenHasher.hash(inviteCode))
			.orElseThrow(() -> new ApiException(
				HttpStatus.BAD_REQUEST,
				"INVITATION_INVALID",
				"유효하지 않은 초대 코드입니다."
			));
		if (invitation.isRevoked()) {
			throw new ApiException(HttpStatus.GONE, "INVITATION_REVOKED", "취소된 초대 코드입니다.");
		}

		var user = identityService.createUser(email, password, displayName);
		memberRepository.save(new LedgerMember(invitation.getLedgerId(), user.getId(), MemberRole.MEMBER));
		invitation.markUsed(clock.instant());
		return new AuthenticatedUser(user.getId(), user.getEmail(), user.getDisplayName(), MemberRole.MEMBER);
	}

	private void verifyBootstrapToken(String suppliedToken) {
		if (bootstrapToken.isBlank() || suppliedToken == null || !MessageDigest.isEqual(
			bootstrapToken.getBytes(StandardCharsets.UTF_8),
			suppliedToken.getBytes(StandardCharsets.UTF_8)
		)) {
			throw new ApiException(HttpStatus.NOT_FOUND, "RESOURCE_NOT_FOUND", "요청한 리소스를 찾을 수 없습니다.");
		}
	}
}
