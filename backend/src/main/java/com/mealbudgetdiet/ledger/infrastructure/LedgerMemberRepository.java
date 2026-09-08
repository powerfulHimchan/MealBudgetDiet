package com.mealbudgetdiet.ledger.infrastructure;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.mealbudgetdiet.ledger.domain.LedgerMember;
import com.mealbudgetdiet.ledger.domain.LedgerMemberId;
import com.mealbudgetdiet.ledger.domain.MemberRole;
import com.mealbudgetdiet.ledger.domain.MemberStatus;

import jakarta.persistence.LockModeType;

public interface LedgerMemberRepository extends JpaRepository<LedgerMember, LedgerMemberId> {

	@Query("""
		select member from LedgerMember member
		where member.id.userId = :userId
		  and member.status = com.mealbudgetdiet.ledger.domain.MemberStatus.ACTIVE
		""")
	Optional<LedgerMember> findActiveByUserId(@Param("userId") UUID userId);

	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("""
		select member from LedgerMember member
		where member.id.ledgerId = :ledgerId and member.id.userId = :userId
		""")
	Optional<LedgerMember> findByLedgerAndUserForUpdate(
		@Param("ledgerId") UUID ledgerId,
		@Param("userId") UUID userId
	);

	@Query("""
		select member from LedgerMember member
		where member.id.ledgerId = :ledgerId
		  and member.status = com.mealbudgetdiet.ledger.domain.MemberStatus.ACTIVE
		order by member.joinedAt asc
		""")
	List<LedgerMember> findAllActiveByLedgerId(@Param("ledgerId") UUID ledgerId);

	List<LedgerMember> findAllByIdLedgerId(UUID ledgerId);

	long countByIdLedgerIdAndStatus(UUID ledgerId, MemberStatus status);

	long countByIdLedgerIdAndStatusAndRole(UUID ledgerId, MemberStatus status, MemberRole role);
}
