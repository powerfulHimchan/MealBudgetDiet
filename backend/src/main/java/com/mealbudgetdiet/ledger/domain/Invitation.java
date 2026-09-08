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

	public Invitation(UUID ledgerId, UUID createdByUserId, String tokenHash) {
		this.id = UUID.randomUUID();
		this.ledgerId = ledgerId;
		this.createdByUserId = createdByUserId;
		this.tokenHash = tokenHash;
	}

	public UUID getLedgerId() {
		return ledgerId;
	}

	public boolean isRevoked() {
		return revokedAt != null;
	}

	public void markUsed(Instant usedAt) {
		useCount++;
		lastUsedAt = usedAt;
	}
}
