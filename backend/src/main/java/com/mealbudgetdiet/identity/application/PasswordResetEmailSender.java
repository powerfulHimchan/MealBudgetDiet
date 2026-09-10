package com.mealbudgetdiet.identity.application;

import java.net.URI;

public interface PasswordResetEmailSender {

	void send(String email, String displayName, URI resetLink);
}
