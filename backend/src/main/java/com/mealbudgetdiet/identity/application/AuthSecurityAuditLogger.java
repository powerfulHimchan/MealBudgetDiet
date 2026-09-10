package com.mealbudgetdiet.identity.application;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class AuthSecurityAuditLogger {

	private static final Logger log = LoggerFactory.getLogger("security.authentication");

	private final AuthSubjectHasher subjectHasher;

	public AuthSecurityAuditLogger(AuthSubjectHasher subjectHasher) {
		this.subjectHasher = subjectHasher;
	}

	public void loginSucceeded(String email, String clientAddress) {
		loginEvent("success", email, clientAddress);
	}

	public void loginFailed(String email, String clientAddress) {
		loginEvent("failure", email, clientAddress);
	}

	public void loginRateLimited(String email, String clientAddress) {
		loginEvent("rate_limited", email, clientAddress);
	}

	public void passwordResetRequested(String email, String clientAddress, boolean allowed) {
		log.atInfo()
			.addKeyValue("event", "password_reset_requested")
			.addKeyValue("outcome", allowed ? "accepted" : "rate_limited")
			.addKeyValue("principal_hash", emailHash(email))
			.addKeyValue("client_hash", clientHash(clientAddress))
			.log("Authentication security event");
	}

	public void passwordResetSucceeded(String email, String clientAddress) {
		log.atInfo()
			.addKeyValue("event", "password_reset_completed")
			.addKeyValue("outcome", "success")
			.addKeyValue("principal_hash", emailHash(email))
			.addKeyValue("client_hash", clientHash(clientAddress))
			.log("Authentication security event");
	}

	public void passwordResetFailed(String clientAddress, String reason) {
		log.atWarn()
			.addKeyValue("event", "password_reset_completed")
			.addKeyValue("outcome", "failure")
			.addKeyValue("reason", reason)
			.addKeyValue("client_hash", clientHash(clientAddress))
			.log("Authentication security event");
	}

	private void loginEvent(String outcome, String email, String clientAddress) {
		log.atInfo()
			.addKeyValue("event", "login")
			.addKeyValue("outcome", outcome)
			.addKeyValue("principal_hash", emailHash(email))
			.addKeyValue("client_hash", clientHash(clientAddress))
			.log("Authentication security event");
	}

	private String emailHash(String email) {
		return subjectHasher.hash("audit-email", IdentityService.normalizeEmail(email));
	}

	private String clientHash(String clientAddress) {
		String safeClient = clientAddress == null || clientAddress.isBlank() ? "unknown" : clientAddress;
		return subjectHasher.hash("audit-client", safeClient);
	}
}
