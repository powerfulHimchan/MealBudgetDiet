package com.mealbudgetdiet.identity.application;

import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.logout.SecurityContextLogoutHandler;
import org.springframework.session.jdbc.JdbcIndexedSessionRepository;
import org.springframework.session.web.http.CookieSerializer;
import org.springframework.session.web.http.CookieSerializer.CookieValue;
import org.springframework.stereotype.Service;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

@Service
public class SessionLogoutService {

	private final CookieSerializer cookieSerializer;
	private final JdbcIndexedSessionRepository sessionRepository;

	public SessionLogoutService(
		CookieSerializer cookieSerializer,
		JdbcIndexedSessionRepository sessionRepository
	) {
		this.cookieSerializer = cookieSerializer;
		this.sessionRepository = sessionRepository;
	}

	public void logout(HttpServletRequest request, HttpServletResponse response) {
		// Delete the sessions identified by the presented cookie as well as the servlet session.
		var sessionIds = new java.util.LinkedHashSet<>(cookieSerializer.readCookieValues(request));
		var session = request.getSession(false);
		if (session != null) sessionIds.add(session.getId());
		new SecurityContextLogoutHandler().logout(
			request,
			response,
			SecurityContextHolder.getContext().getAuthentication()
		);
		for (String sessionId : sessionIds) {
			sessionRepository.deleteById(sessionId);
		}
		cookieSerializer.writeCookieValue(new CookieValue(request, response, ""));
	}
}
