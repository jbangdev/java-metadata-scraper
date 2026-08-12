package dev.jbang.jdkdb.util;

import java.io.IOException;

public class HttpStatusException extends IOException {
	private final int statusCode;

	public HttpStatusException(int statusCode, String message) {
		super(message);
		this.statusCode = statusCode;
	}

	public int getStatusCode() {
		return statusCode;
	}
}
