package com.mealbudgetdiet.identity.infrastructure;

import java.net.URI;
import java.time.Duration;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;

import com.mealbudgetdiet.identity.application.PasswordResetEmailSender;

@Component
@ConditionalOnProperty(name = "app.password-reset.email-enabled", havingValue = "true")
public class SmtpPasswordResetEmailSender implements PasswordResetEmailSender {

	private final JavaMailSender mailSender;
	private final String from;
	private final Duration tokenTtl;

	public SmtpPasswordResetEmailSender(
		JavaMailSender mailSender,
		@Value("${app.password-reset.email-from}") String from,
		@Value("${app.password-reset.token-ttl:30m}") Duration tokenTtl
	) {
		this.mailSender = mailSender;
		this.from = from;
		this.tokenTtl = tokenTtl;
	}

	@Override
	public void send(String email, String displayName, URI resetLink) {
		var message = new SimpleMailMessage();
		message.setFrom(from);
		message.setTo(email);
		message.setSubject("[MealBudgetDiet] 비밀번호 재설정 안내");
		message.setText("""
			%s님, 아래 링크에서 MealBudgetDiet 비밀번호를 재설정해 주세요.

			%s

			링크는 %s 동안 한 번만 사용할 수 있습니다. 요청하지 않았다면 이 메일을 무시해 주세요.
			""".formatted(displayName, resetLink, expiryLabel()));
		mailSender.send(message);
	}

	private String expiryLabel() {
		long minutes = Math.max(1, tokenTtl.toMinutes());
		return minutes >= 60 && minutes % 60 == 0 ? minutes / 60 + "시간" : minutes + "분";
	}
}
