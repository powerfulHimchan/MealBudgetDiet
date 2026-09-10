package com.mealbudgetdiet.shared.config;

import java.io.IOException;
import java.util.Set;

import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.session.web.http.CookieSerializer;
import org.springframework.session.web.http.CookieSerializer.CookieValue;
import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

final class PersistentSessionCookieFilter extends OncePerRequestFilter {

	private static final Set<String> SESSION_COOKIE_MANAGED_PATHS = Set.of(
		"/api/v1/bootstrap/admin",
		"/api/v1/auth/login",
		"/api/v1/auth/logout",
		"/api/v1/auth/register",
		"/api/v1/auth/password-resets",
		"/api/v1/account/password-change",
		"/api/v1/account/withdrawal"
	);

	private final CookieSerializer cookieSerializer;

	PersistentSessionCookieFilter(CookieSerializer cookieSerializer) {
		this.cookieSerializer = cookieSerializer;
	}

	@Override
	protected void doFilterInternal(
		HttpServletRequest request,
		HttpServletResponse response,
		FilterChain filterChain
	) throws ServletException, IOException {
		var authentication = SecurityContextHolder.getContext().getAuthentication();
		var session = request.getSession(false);
		if (session != null && authentication != null && authentication.isAuthenticated()
			&& !(authentication instanceof AnonymousAuthenticationToken)) {
			cookieSerializer.writeCookieValue(new CookieValue(request, response, session.getId()));
		}
		filterChain.doFilter(request, response);
	}

	@Override
	protected boolean shouldNotFilter(HttpServletRequest request) {
		String requestPath = request.getRequestURI().substring(request.getContextPath().length());
		return SESSION_COOKIE_MANAGED_PATHS.contains(requestPath);
	}
}
