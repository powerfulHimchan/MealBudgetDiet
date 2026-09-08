package com.mealbudgetdiet.ledger.domain;

import java.io.Serializable;
import java.util.Objects;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

@Embeddable
public class LedgerMemberId implements Serializable {

	@Column(name = "ledger_id")
	private UUID ledgerId;

	@Column(name = "user_id")
	private UUID userId;

	protected LedgerMemberId() {
	}

	public LedgerMemberId(UUID ledgerId, UUID userId) {
		this.ledgerId = ledgerId;
		this.userId = userId;
	}

	public UUID getLedgerId() {
		return ledgerId;
	}

	public UUID getUserId() {
		return userId;
	}

	@Override
	public boolean equals(Object other) {
		if (this == other) {
			return true;
		}
		if (!(other instanceof LedgerMemberId that)) {
			return false;
		}
		return Objects.equals(ledgerId, that.ledgerId) && Objects.equals(userId, that.userId);
	}

	@Override
	public int hashCode() {
		return Objects.hash(ledgerId, userId);
	}
}
