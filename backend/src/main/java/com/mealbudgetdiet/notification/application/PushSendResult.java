package com.mealbudgetdiet.notification.application;

public record PushSendResult(boolean success, boolean expired, String error) {
	public static PushSendResult sent() {
		return new PushSendResult(true, false, null);
	}

	public static PushSendResult failed(boolean expired, String error) {
		return new PushSendResult(false, expired, error);
	}
}
