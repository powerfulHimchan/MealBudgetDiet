package com.mealbudgetdiet.ledger.infrastructure;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.mealbudgetdiet.ledger.domain.Ledger;

public interface LedgerRepository extends JpaRepository<Ledger, UUID> {
}
