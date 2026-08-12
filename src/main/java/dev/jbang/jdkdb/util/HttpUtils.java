package dev.jbang.jdkdb.util;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.FileTime;
import java.time.Duration;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Utility class for HTTP operations */
public class HttpUtils {

	private static final Logger logger = LoggerFactory.getLogger(HttpUtils.class);

	/** Functional interface for operations that can throw IOException and InterruptedException */
	@FunctionalInterface
	private interface IOSupplier<T> {
		T get() throws IOException, InterruptedException;
	}

	private final HttpClient httpClient;

	public static final String GITHUB_TOKEN_PROP = "github.token";
	private static final int DEFAULT_MAX_RETRIES = 3;
	private static final Duration INITIAL_BACKOFF = Duration.ofSeconds(2);
	private static final Duration MAX_REQUEST_TIMEOUT = Duration.ofMinutes(10);

	public HttpUtils() {
		this.httpClient = HttpClient.newBuilder()
				.followRedirects(HttpClient.Redirect.NORMAL)
				.connectTimeout(Duration.ofSeconds(30))
				.build();
	}

	/** Download a file from a URL to a local path */
	public Path downloadFile(String url, Path destination) throws IOException, InterruptedException {
		return retry(() -> {
			HttpRequest request = request(url).build();
			HttpResponse<Path> response = httpClient.send(
					request,
					HttpResponse.BodyHandlers.ofFile(
							destination,
							StandardOpenOption.CREATE,
							StandardOpenOption.WRITE,
							StandardOpenOption.TRUNCATE_EXISTING));
			if (response.statusCode() < 200 || response.statusCode() >= 300) {
				throw new HttpStatusException(
						response.statusCode(),
						"Failed to download file: " + url + " - HTTP status: " + response.statusCode());
			}

			// Preserve original file timestamp from Last-Modified header if available
			response.headers().firstValue("Last-Modified").ifPresent(lastModified -> {
				try {
					Instant instant = Instant.from(DateTimeFormatter.RFC_1123_DATE_TIME.parse(lastModified));
					Files.setLastModifiedTime(destination, FileTime.from(instant));
				} catch (Exception e) {
					// Silently ignore if we can't parse or set the timestamp
				}
			});
			return response.body();
		});
	}

	/** Download content from a URL as a string */
	public String downloadString(String url) throws IOException, InterruptedException {
		return retry(() -> {
			HttpRequest request = request(url).build();
			HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
			if (response.statusCode() < 200 || response.statusCode() >= 300) {
				throw new HttpStatusException(
						response.statusCode(),
						"Failed to download content: " + url + " - HTTP status: " + response.statusCode());
			}
			return response.body();
		});
	}

	/** Check if a URL exists (returns 2xx status code) */
	public boolean urlExists(String url) {
		try {
			HttpRequest request = request(url)
					.method("HEAD", HttpRequest.BodyPublishers.noBody())
					.build();
			HttpResponse<Void> response = httpClient.send(request, HttpResponse.BodyHandlers.discarding());
			int statusCode = response.statusCode();
			return statusCode >= 200 && statusCode < 300;
		} catch (IOException | InterruptedException e) {
			return false;
		}
	}

	private HttpRequest.Builder request(String url) {
		URI uri = URI.create(url);
		HttpRequest.Builder builder = HttpRequest.newBuilder().uri(uri).GET().timeout(MAX_REQUEST_TIMEOUT);

		// If the URL is for GitHub API, add the Authorization header if a token is available
		if (uri.getHost().equalsIgnoreCase("api.github.com")) {
			String token = System.getProperty(GITHUB_TOKEN_PROP);
			if (token != null && !token.isEmpty()) {
				builder.header("Authorization", "Bearer " + token);
			}
		}

		return builder;
	}

	/**
	 * Retry an operation with exponential backoff
	 *
	 * @param operation The operation to retry
	 * @return The result of the operation
	 * @throws IOException If all retry attempts fail
	 * @throws InterruptedException If the thread is interrupted during backoff
	 */
	private <T> T retry(IOSupplier<T> operation) throws IOException, InterruptedException {
		IOException lastException = null;
		for (int attempt = 0; attempt < DEFAULT_MAX_RETRIES; attempt++) {
			try {
				return operation.get();
			} catch (IOException e) {
				lastException = e;
				if (e instanceof HttpTimeoutException) {
					// Don't retry on timeout — the request already took the maximum allowed time
					throw e;
				}
				if (e instanceof HttpStatusException) {
					int statusCode = ((HttpStatusException) e).getStatusCode();
					// Don't retry for client errors (4xx) except 429 Too Many Requests
					if (statusCode >= 400 && statusCode < 500 && statusCode != 429) {
						throw e;
					}
				}
				if (attempt < DEFAULT_MAX_RETRIES - 1) {
					// Exponential backoff: 2s, 4s, 8s, ...
					long backoffMillis = INITIAL_BACKOFF.toMillis() * (1L << attempt);
					logger.info(
							"HTTP operation failed (attempt {}/{}): {} - retrying after {}ms",
							attempt + 1,
							DEFAULT_MAX_RETRIES,
							e.getMessage(),
							backoffMillis);
					Thread.sleep(backoffMillis);
				}
			}
		}
		throw lastException;
	}
}
