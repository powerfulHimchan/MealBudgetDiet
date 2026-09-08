package com.mealbudgetdiet.ledger.infrastructure;

import org.springframework.data.jpa.repository.JpaRepository;

import com.mealbudgetdiet.ledger.domain.LedgerMember;
import com.mealbudgetdiet.ledger.domain.LedgerMemberId;

public interface LedgerMemberRepository extends JpaRepository<LedgerMember, LedgerMemberId> {
}
