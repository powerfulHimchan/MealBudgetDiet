package com.mealbudgetdiet.shared.api;

import java.net.URI;
import java.util.List;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import jakarta.servlet.http.HttpServletRequest;

@RestControllerAdvice
public class ApiExceptionHandler {

	@ExceptionHandler(ApiException.class)
	ProblemDetail handleApiException(ApiException exception, HttpServletRequest request) {
		return problem(exception.getStatus(), exception.getCode(), exception.getMessage(), request);
	}

	@ExceptionHandler(MethodArgumentNotValidException.class)
	ProblemDetail handleValidation(MethodArgumentNotValidException exception, HttpServletRequest request) {
		var problem = problem(HttpStatus.BAD_REQUEST, "VALIDATION_FAILED", "입력값을 확인해 주세요.", request);
		List<FieldError> errors = exception.getBindingResult().getFieldErrors().stream()
			.map(error -> new FieldError(error.getField(), error.getDefaultMessage()))
			.toList();
		problem.setProperty("errors", errors);
		return problem;
	}

	@ExceptionHandler(DataIntegrityViolationException.class)
	ProblemDetail handleConflict(DataIntegrityViolationException exception, HttpServletRequest request) {
		return problem(HttpStatus.CONFLICT, "RESOURCE_CONFLICT", "이미 사용 중인 값입니다.", request);
	}

	@ExceptionHandler(ObjectOptimisticLockingFailureException.class)
	ProblemDetail handleOptimisticLock(
		ObjectOptimisticLockingFailureException exception,
		HttpServletRequest request
	) {
		return problem(
			HttpStatus.CONFLICT,
			"VERSION_CONFLICT",
			"다른 사용자가 먼저 변경했습니다. 최신 내용을 확인해 주세요.",
			request
		);
	}

	@ExceptionHandler(MaxUploadSizeExceededException.class)
	ProblemDetail handleUploadLimit(MaxUploadSizeExceededException exception, HttpServletRequest request) {
		return problem(
			HttpStatus.PAYLOAD_TOO_LARGE,
			"IMAGE_TOO_LARGE",
			"이미지는 파일당 5MB 이하여야 합니다.",
			request
		);
	}

	private ProblemDetail problem(HttpStatus status, String code, String detail, HttpServletRequest request) {
		var problem = ProblemDetail.forStatusAndDetail(status, detail);
		problem.setType(URI.create("https://mealbudgetdiet.app/problems/" + code.toLowerCase().replace('_', '-')));
		problem.setTitle(status.getReasonPhrase());
		problem.setInstance(URI.create(request.getRequestURI()));
		problem.setProperty("code", code);
		return problem;
	}

	private record FieldError(String field, String reason) {
	}
}
