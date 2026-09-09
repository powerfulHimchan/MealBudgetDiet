package com.mealbudgetdiet.media.infrastructure;

public interface ImageObjectStore {
	void put(String key, byte[] content, String contentType);
	byte[] get(String key);
	void delete(String key);
}
