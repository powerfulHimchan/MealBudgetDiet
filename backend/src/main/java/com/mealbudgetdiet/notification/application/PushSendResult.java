package com.mealbudgetdiet.notification.application;

public record PushSendResult(boolean success, boolean expired, boolean retryable, String error) {
	public static PushSendResult sent() {
		return new PushSendResult(true, false, false, null);
	}

	public static PushSendResult failed(boolean expired, boolean retryable, String error) {
		return new PushSendResult(false, expired, retryable, error);
	}
}
