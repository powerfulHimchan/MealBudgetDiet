package com.mealbudgetdiet.identity.application;

import java.util.Collection;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

@Service
public class SessionInvalidationService {

	private final JdbcTemplate jdbcTemplate;

	public SessionInvalidationService(JdbcTemplate jdbcTemplate) {
		this.jdbcTemplate = jdbcTemplate;
	}

	public void invalidateByEmails(Collection<String> emails) {
		for (String email : emails) {
			jdbcTemplate.update("delete from spring_session where principal_name = ?", email);
		}
	}
}
