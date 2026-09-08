package com.mealbudgetdiet.ledger.domain;

import java.time.Instant;
import java.util.UUID;

import org.hibernate.annotations.CreationTimestamp;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "invitations")
public class Invitation {

	@Id
	private UUID id;

	@Column(name = "ledger_id", nullable = false)
	private UUID ledgerId;

	@Column(name = "created_by_user_id")
	private UUID createdByUserId;

	@Column(name = "token_hash", nullable = false, length = 64, unique = true)
	private String tokenHash;

	@Column(name = "code_suffix", nullable = false, length = 4)
	private String codeSuffix;

	@Column(name = "revoked_at")
	private Instant revokedAt;

	@Column(name = "use_count", nullable = false)
	private long useCount;

	@Column(name = "last_used_at")
	private Instant lastUsedAt;

	@CreationTimestamp
	@Column(name = "created_at", nullable = false, updatable = false)
	private Instant createdAt;

	protected Invitation() {
	}

	public Invitation(UUID ledgerId, UUID createdByUserId, String tokenHash, String codeSuffix) {
		this.id = UUID.randomUUID();
		this.ledgerId = ledgerId;
		this.createdByUserId = createdByUserId;
		this.tokenHash = tokenHash;
		this.codeSuffix = codeSuffix;
	}

	public UUID getId() {
		return id;
	}

	public UUID getLedgerId() {
		return ledgerId;
	}

	public boolean isRevoked() {
		return revokedAt != null;
	}

	public String getCodeSuffix() {
		return codeSuffix;
	}

	public long getUseCount() {
		return useCount;
	}

	public Instant getLastUsedAt() {
		return lastUsedAt;
	}

	public Instant getCreatedAt() {
		return createdAt;
	}

	public Instant getRevokedAt() {
		return revokedAt;
	}

	public void revoke(Instant revokedAt) {
		if (this.revokedAt == null) {
			this.revokedAt = revokedAt;
		}
	}

	public void markUsed(Instant usedAt) {
		useCount++;
		lastUsedAt = usedAt;
	}
}
