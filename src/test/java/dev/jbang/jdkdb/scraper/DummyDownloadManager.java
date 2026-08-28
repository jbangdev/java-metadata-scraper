package dev.jbang.jdkdb.scraper;

import dev.jbang.jdkdb.model.JdkMetadata;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;

/**
 * Dummy implementation of DownloadManager for testing. Does not actually download files, but
 * tracks submitted downloads for verification in tests.
 */
public class DummyDownloadManager extends DownloadManager {
	private final List<SubmittedDownload> submittedDownloads = new ArrayList<>();

	/**
	 * Create a new DummyDownloadManager.
	 */
	public DummyDownloadManager() {}

	@Override
	public void submit(JdkMetadata metadata, String distro, Logger downloadLogger) {
		submittedDownloads.add(new SubmittedDownload(metadata, distro, downloadLogger));
	}

	@Override
	public void start() {
		// No-op for testing
	}

	@Override
	public void shutdown() {
		// No-op for testing
	}

	@Override
	public void awaitCompletion() {
		// No-op for testing
	}

	@Override
	public int getCompletedCount() {
		return 0;
	}

	@Override
	public int getFailedCount() {
		return 0;
	}

	/**
	 * No-op: DummyDownloadManager never actually processes tasks.
	 */
	@Override
	protected void processDownload(DownloadTask task) {
		// No-op
	}

	/**
	 * Get all submitted downloads for verification in tests.
	 *
	 * @return List of all submitted downloads
	 */
	public List<SubmittedDownload> getSubmittedDownloads() {
		return new ArrayList<>(submittedDownloads);
	}

	/**
	 * Get the count of submitted downloads.
	 *
	 * @return Number of downloads submitted
	 */
	public int getSubmittedCount() {
		return submittedDownloads.size();
	}

	/**
	 * Get per-distro download statistics.
	 *
	 * @return Map of distro name to statistics
	 */
	@Override
	public Map<String, DistroStats> getDistroStats() {
		Map<String, DistroStats> stats = new HashMap<>();
		Map<String, Integer> distroCounts = new HashMap<>();

		// Count submissions per distro
		for (SubmittedDownload download : submittedDownloads) {
			distroCounts.merge(download.distro, 1, Integer::sum);
		}

		// Create stats (all submissions are treated as completed in dummy mode)
		for (Map.Entry<String, Integer> entry : distroCounts.entrySet()) {
			stats.put(entry.getKey(), new DistroStats(entry.getKey(), entry.getValue(), entry.getValue(), 0));
		}

		return stats;
	}

	/** Record of a submitted download */
	public record SubmittedDownload(JdkMetadata metadata, String distro, Logger downloadLogger) {}
}
