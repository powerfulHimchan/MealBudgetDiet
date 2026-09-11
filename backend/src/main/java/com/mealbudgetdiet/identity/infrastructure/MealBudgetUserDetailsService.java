package com.mealbudgetdiet.identity.infrastructure;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import com.mealbudgetdiet.identity.application.IdentityService;
import com.mealbudgetdiet.identity.domain.ServiceRole;
import com.mealbudgetdiet.ledger.domain.MemberRole;

@Service
public class MealBudgetUserDetailsService implements UserDetailsService {

	private final JdbcClient jdbcClient;

	public MealBudgetUserDetailsService(JdbcClient jdbcClient) {
		this.jdbcClient = jdbcClient;
	}

	@Override
	public UserDetails loadUserByUsername(String email) throws UsernameNotFoundException {
		return jdbcClient.sql("""
			select u.id, u.email, u.password_hash, u.display_name, u.service_role, lm.role
			from users u
			join ledger_members lm on lm.user_id = u.id
			where u.email = :email
			  and u.status = 'ACTIVE'
			  and lm.status = 'ACTIVE'
			limit 1
			""")
			.param("email", IdentityService.normalizeEmail(email))
			.query((resultSet, rowNumber) -> new MealBudgetPrincipal(
				resultSet.getObject("id", java.util.UUID.class),
				resultSet.getString("email"),
				resultSet.getString("password_hash"),
				resultSet.getString("display_name"),
				ServiceRole.valueOf(resultSet.getString("service_role")),
				MemberRole.valueOf(resultSet.getString("role"))
			))
			.optional()
			.orElseThrow(() -> new UsernameNotFoundException("Invalid credentials"));
	}
}
