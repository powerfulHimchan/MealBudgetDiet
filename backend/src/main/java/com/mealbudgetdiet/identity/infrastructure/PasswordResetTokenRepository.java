package com.mealbudgetdiet.identity.infrastructure;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.mealbudgetdiet.identity.domain.PasswordResetToken;

import jakarta.persistence.LockModeType;

public interface PasswordResetTokenRepository extends JpaRepository<PasswordResetToken, UUID> {

	Optional<PasswordResetToken> findByTokenHash(String tokenHash);

	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select token from PasswordResetToken token where token.tokenHash = :tokenHash")
	Optional<PasswordResetToken> findByTokenHashForUpdate(@Param("tokenHash") String tokenHash);

	@Modifying(clearAutomatically = true, flushAutomatically = true)
	@Query("""
		update PasswordResetToken token set token.usedAt = :usedAt
		where token.userId = :userId and token.usedAt is null
		""")
	int markUnusedTokensUsed(@Param("userId") UUID userId, @Param("usedAt") Instant usedAt);
}
