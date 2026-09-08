package com.mealbudgetdiet.shared.config;

import java.io.IOException;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfTokenRequestAttributeHandler;

import jakarta.servlet.http.HttpServletResponse;

@Configuration(proxyBeanMethods = false)
public class SecurityConfig {

	@Bean
	SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
		var csrfRepository = CookieCsrfTokenRepository.withHttpOnlyFalse();
		csrfRepository.setCookiePath("/");

		return http
			.authorizeHttpRequests(authorize -> authorize
				.requestMatchers(
					"/actuator/health/**",
					"/api/v1/system/health",
					"/api/v1/bootstrap/**",
					"/api/v1/auth/csrf",
					"/api/v1/auth/login",
					"/api/v1/auth/register"
				).permitAll()
				.anyRequest().authenticated())
			.csrf(csrf -> csrf
				.csrfTokenRepository(csrfRepository)
				.csrfTokenRequestHandler(new CsrfTokenRequestAttributeHandler()))
			.securityContext(context -> context.securityContextRepository(securityContextRepository()))
			.exceptionHandling(exceptions -> exceptions
				.authenticationEntryPoint((request, response, exception) ->
					writeProblem(response, HttpServletResponse.SC_UNAUTHORIZED, "AUTHENTICATION_REQUIRED", "로그인이 필요합니다."))
				.accessDeniedHandler((request, response, exception) ->
					writeProblem(response, HttpServletResponse.SC_FORBIDDEN, "ACCESS_DENIED", "요청 권한이 없습니다.")))
			.build();
	}

	@Bean
	PasswordEncoder passwordEncoder() {
		return PasswordEncoderFactories.createDelegatingPasswordEncoder();
	}

	@Bean
	AuthenticationManager authenticationManager(AuthenticationConfiguration configuration) throws Exception {
		return configuration.getAuthenticationManager();
	}

	@Bean
	SecurityContextRepository securityContextRepository() {
		return new HttpSessionSecurityContextRepository();
	}

	private static void writeProblem(HttpServletResponse response, int status, String code, String detail)
		throws IOException {
		response.setStatus(status);
		response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
		response.setCharacterEncoding("UTF-8");
		response.getWriter().write("""
			{"status":%d,"code":"%s","detail":"%s"}
			""".formatted(status, code, detail));
	}
}
