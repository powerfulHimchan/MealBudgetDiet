package com.mealbudgetdiet.ledger.domain;

import java.time.Instant;
import java.util.UUID;

import org.hibernate.annotations.CreationTimestamp;

import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;

@Entity
@Table(name = "ledger_members")
public class LedgerMember {

	@EmbeddedId
	private LedgerMemberId id;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 20)
	private MemberRole role;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 20)
	private MemberStatus status;

	@CreationTimestamp
	@Column(name = "joined_at", nullable = false, updatable = false)
	private Instant joinedAt;

	@Column(name = "left_at")
	private Instant leftAt;

	protected LedgerMember() {
	}

	public LedgerMember(UUID ledgerId, UUID userId, MemberRole role) {
		this.id = new LedgerMemberId(ledgerId, userId);
		this.role = role;
		this.status = MemberStatus.ACTIVE;
	}

	public MemberRole getRole() {
		return role;
	}

	public LedgerMemberId getId() {
		return id;
	}

	public MemberStatus getStatus() {
		return status;
	}

	public Instant getJoinedAt() {
		return joinedAt;
	}

	public void changeRole(MemberRole role) {
		this.role = role;
	}

	public void leave(Instant leftAt) {
		this.status = MemberStatus.LEFT;
		this.leftAt = leftAt;
	}

	public void rejoin(MemberRole role, Instant joinedAt) {
		this.role = role;
		this.status = MemberStatus.ACTIVE;
		this.joinedAt = joinedAt;
		this.leftAt = null;
	}
}
