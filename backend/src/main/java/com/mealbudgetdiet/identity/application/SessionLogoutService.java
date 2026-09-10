package com.mealbudgetdiet.identity.application;

import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.logout.SecurityContextLogoutHandler;
import org.springframework.session.web.http.CookieSerializer;
import org.springframework.session.web.http.CookieSerializer.CookieValue;
import org.springframework.stereotype.Service;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

@Service
public class SessionLogoutService {

	private final CookieSerializer cookieSerializer;

	public SessionLogoutService(CookieSerializer cookieSerializer) {
		this.cookieSerializer = cookieSerializer;
	}

	public void logout(HttpServletRequest request, HttpServletResponse response) {
		new SecurityContextLogoutHandler().logout(
			request,
			response,
			SecurityContextHolder.getContext().getAuthentication()
		);
		cookieSerializer.writeCookieValue(new CookieValue(request, response, ""));
	}
}
