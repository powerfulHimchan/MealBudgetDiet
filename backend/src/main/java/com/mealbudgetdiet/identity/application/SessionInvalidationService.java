package com.mealbudgetdiet.identity.application;

import java.util.Collection;

import org.springframework.session.FindByIndexNameSessionRepository;
import org.springframework.session.jdbc.JdbcIndexedSessionRepository;
import org.springframework.stereotype.Service;

@Service
public class SessionInvalidationService {

	private final JdbcIndexedSessionRepository sessionRepository;

	public SessionInvalidationService(JdbcIndexedSessionRepository sessionRepository) {
		this.sessionRepository = sessionRepository;
	}

	public void invalidateByEmails(Collection<String> emails) {
		for (String email : emails) {
			sessionRepository.findByIndexNameAndIndexValue(
				FindByIndexNameSessionRepository.PRINCIPAL_NAME_INDEX_NAME,
				email
			).keySet().forEach(sessionRepository::deleteById);
		}
	}
}
