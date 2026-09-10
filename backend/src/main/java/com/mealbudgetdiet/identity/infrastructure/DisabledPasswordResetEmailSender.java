package com.mealbudgetdiet.identity.infrastructure;

import java.net.URI;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import com.mealbudgetdiet.identity.application.PasswordResetEmailSender;

@Component
@ConditionalOnProperty(
	name = "app.password-reset.email-enabled",
	havingValue = "false",
	matchIfMissing = true
)
public class DisabledPasswordResetEmailSender implements PasswordResetEmailSender {

	@Override
	public void send(String email, String displayName, URI resetLink) {
		// Intentionally disabled. Never log raw reset links or tokens.
	}
}
