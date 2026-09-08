package com.mealbudgetdiet.ledger.infrastructure;

import java.util.UUID;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.mealbudgetdiet.ledger.domain.Ledger;

import jakarta.persistence.LockModeType;

public interface LedgerRepository extends JpaRepository<Ledger, UUID> {

	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select ledger from Ledger ledger where ledger.id = :ledgerId")
	Optional<Ledger> findByIdForUpdate(@Param("ledgerId") UUID ledgerId);
}
