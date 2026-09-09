package com.mealbudgetdiet.notification.infrastructure;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.mealbudgetdiet.notification.domain.PushSubscription;

public interface PushSubscriptionRepository extends JpaRepository<PushSubscription, UUID> {

	Optional<PushSubscription> findByEndpoint(String endpoint);

	Optional<PushSubscription> findByIdAndUserId(UUID id, UUID userId);

	@Query("""
		select subscription from PushSubscription subscription, LedgerMember member
		where subscription.userId = member.id.userId
		  and member.id.ledgerId = :ledgerId
		  and member.status = com.mealbudgetdiet.ledger.domain.MemberStatus.ACTIVE
		  and subscription.status = com.mealbudgetdiet.notification.domain.PushSubscriptionStatus.ACTIVE
		""")
	List<PushSubscription> findAllActiveForLedger(@Param("ledgerId") UUID ledgerId);
}
