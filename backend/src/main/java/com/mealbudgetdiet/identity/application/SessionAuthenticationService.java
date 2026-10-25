package com.mealbudgetdiet.identity.application;

import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.stereotype.Service;

import com.mealbudgetdiet.identity.infrastructure.MealBudgetPrincipal;
import com.mealbudgetdiet.shared.api.ApiException;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

@Service
public class SessionAuthenticationService {

	private final AuthenticationManager authenticationManager;
	private final SecurityContextRepository securityContextRepository;
	private final JdbcTemplate jdbcTemplate;

	public SessionAuthenticationService(
		AuthenticationManager authenticationManager,
		SecurityContextRepository securityContextRepository,
		JdbcTemplate jdbcTemplate
	) {
		this.authenticationManager = authenticationManager;
		this.securityContextRepository = securityContextRepository;
		this.jdbcTemplate = jdbcTemplate;
	}

	public MealBudgetPrincipal login(
		String email,
		String password,
		HttpServletRequest request,
		HttpServletResponse response
	) {
		try {
			var authentication = authenticationManager.authenticate(
				UsernamePasswordAuthenticationToken.unauthenticated(IdentityService.normalizeEmail(email), password)
			);
			var context = SecurityContextHolder.createEmptyContext();
			context.setAuthentication(authentication);
			SecurityContextHolder.setContext(context);
			var existingSession = request.getSession(false);
			if (existingSession != null) {
				String previousSessionId = existingSession.getId();
				existingSession.invalidate();
				// Account switches must revoke capabilities of non-expiring sessions too.
				jdbcTemplate.update("delete from spring_session where session_id = ?", previousSessionId);
			}
			securityContextRepository.saveContext(context, request, response);
			return (MealBudgetPrincipal) authentication.getPrincipal();
		}
		catch (AuthenticationException exception) {
			throw new ApiException(HttpStatus.UNAUTHORIZED, "INVALID_CREDENTIALS", "이메일 또는 비밀번호가 올바르지 않습니다.");
		}
	}
}
