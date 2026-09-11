package com.mealbudgetdiet.identity.infrastructure;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.mealbudgetdiet.identity.domain.ServiceRole;
import com.mealbudgetdiet.identity.domain.User;
import com.mealbudgetdiet.identity.domain.UserStatus;

import jakarta.persistence.LockModeType;

public interface UserRepository extends JpaRepository<User, UUID> {

	Optional<User> findByEmail(String email);

	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select account from User account where account.email = :email")
	Optional<User> findByEmailForUpdate(@Param("email") String email);

	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select account from User account where account.id = :id")
	Optional<User> findByIdForUpdate(@Param("id") UUID id);

	boolean existsByEmail(String email);

	boolean existsByServiceRoleAndStatus(ServiceRole serviceRole, UserStatus status);
}
