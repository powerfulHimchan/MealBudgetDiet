package com.mealbudgetdiet.identity.infrastructure;

import java.io.Serializable;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import com.mealbudgetdiet.identity.domain.ServiceRole;
import com.mealbudgetdiet.ledger.domain.MemberRole;

public record MealBudgetPrincipal(
	UUID id,
	String email,
	String passwordHash,
	String displayName,
	ServiceRole serviceRole,
	MemberRole ledgerRole
) implements UserDetails, Serializable {

	@Override
	public Collection<? extends GrantedAuthority> getAuthorities() {
		return List.of(
			new SimpleGrantedAuthority("ROLE_" + serviceRole.name()),
			new SimpleGrantedAuthority("ROLE_LEDGER_" + ledgerRole.name())
		);
	}

	@Override
	public String getPassword() {
		return passwordHash;
	}

	@Override
	public String getUsername() {
		return email;
	}

	@Override
	public boolean isAccountNonExpired() {
		return true;
	}

	@Override
	public boolean isAccountNonLocked() {
		return true;
	}

	@Override
	public boolean isCredentialsNonExpired() {
		return true;
	}

	@Override
	public boolean isEnabled() {
		return true;
	}
}
