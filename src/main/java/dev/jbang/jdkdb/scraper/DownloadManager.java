package dev.jbang.jdkdb.scraper;

import dev.jbang.jdkdb.model.JdkMetadata;
import java.io.IOException;
import java.util.Map;
import org.slf4j.Logger;

/**
 * Abstract base class for managing parallel downloads of JDK files. Implementations receive
 * JdkMetadata from scrapers and handle the downloading of files. Subclasses implement
 * {@link #processDownload(DownloadTask)} to supply the actual per-task processing logic and may
 * override {@link #createDistroStats} to return extended per-distro statistics.
 */
public abstract class DownloadManager {
	/**
	 * Submit a metadata item for download.
	 *
	 * @param metadata The JDK metadata containing the URL to download
	 * @param distro   The distro name
	 * @param logger   The logger to use for logging download progress and errors
	 */
	public abstract void submit(JdkMetadata metadata, String distro, Logger logger);

	/**
	 * Start the download manager. Should be called once after construction.
	 */
	public abstract void start();

	/**
	 * Signal that no more downloads will be submitted. Call this after all scrapers have finished.
	 */
	public abstract void shutdown();

	/**
	 * Wait for all queued downloads to complete. This method blocks until all downloads are
	 * finished.
	 *
	 * @throws InterruptedException if interrupted while waiting
	 */
	public abstract void awaitCompletion() throws InterruptedException;

	/**
	 * Get the number of completed downloads.
	 *
	 * @return Number of successfully completed downloads
	 */
	public abstract int getCompletedCount();

	/**
	 * Get the number of failed downloads.
	 *
	 * @return Number of failed downloads
	 */
	public abstract int getFailedCount();

	/**
	 * Get per-distro download statistics. The returned values are at least {@link DistroStats};
	 * concrete implementations may return a richer subtype.
	 *
	 * @return Map of distro name to statistics
	 */
	public abstract Map<String, ? extends DistroStats> getDistroStats();

	/**
	 * Process a single download task. Implementations perform the actual work (e.g., HTTP download,
	 * checksum computation, URL verification).
	 *
	 * @param task The download task to process
	 * @throws IOException          if an I/O error occurs
	 * @throws InterruptedException if interrupted while processing
	 */
	protected abstract void processDownload(DownloadTask task) throws IOException, InterruptedException;

	// -------------------------------------------------------------------------
	// Shared types
	// -------------------------------------------------------------------------

	/** Internal record representing a single download task. */
	public record DownloadTask(JdkMetadata metadata, String distro, Logger downloadLogger) {}

	/**
	 * Statistics for a single distro's downloads. May be subclassed by concrete
	 * {@link DownloadManager} implementations to carry additional fields.
	 */
	public static class DistroStats {
		protected final String distro;
		protected final int submitted;
		protected final int completed;
		protected final int failed;

		public DistroStats(String distro, int submitted, int completed, int failed) {
			this.distro = distro;
			this.submitted = submitted;
			this.completed = completed;
			this.failed = failed;
		}

		/**
		 * Get the distro name.
		 *
		 * @return The distro name
		 */
		public String distro() {
			return distro;
		}

		/**
		 * Get the number of downloads that were submitted.
		 *
		 * @return Number of downloads submitted for this distro
		 */
		public int submitted() {
			return submitted;
		}

		/**
		 * Get the number of downloads that completed successfully.
		 *
		 * @return Number of successful downloads for this distro
		 */
		public int completed() {
			return completed;
		}

		/**
		 * Get the number of downloads that failed.
		 *
		 * @return Number of failed downloads for this distro
		 */
		public int failed() {
			return failed;
		}

		/**
		 * Get the number of downloads still pending (submitted but not completed or failed).
		 *
		 * @return Number of pending downloads for this distro
		 */
		public int pending() {
			return submitted - completed - failed;
		}
	}
}
