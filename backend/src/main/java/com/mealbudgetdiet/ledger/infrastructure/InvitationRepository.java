package com.mealbudgetdiet.ledger.infrastructure;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.mealbudgetdiet.ledger.domain.Invitation;

import jakarta.persistence.LockModeType;

public interface InvitationRepository extends JpaRepository<Invitation, UUID> {

	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select invitation from Invitation invitation where invitation.tokenHash = :tokenHash")
	Optional<Invitation> findByTokenHashForUpdate(@Param("tokenHash") String tokenHash);

	List<Invitation> findAllByLedgerIdOrderByCreatedAtDesc(UUID ledgerId);

	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("""
		select invitation from Invitation invitation
		where invitation.id = :invitationId and invitation.ledgerId = :ledgerId
		""")
	Optional<Invitation> findByIdAndLedgerIdForUpdate(
		@Param("invitationId") UUID invitationId,
		@Param("ledgerId") UUID ledgerId
	);
}
