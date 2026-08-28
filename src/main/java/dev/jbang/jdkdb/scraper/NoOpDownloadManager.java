package dev.jbang.jdkdb.scraper;

import dev.jbang.jdkdb.model.JdkMetadata;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * No-operation implementation of DownloadManager that skips all downloads. Useful for testing or
 * metadata-only scraping.
 */
public class NoOpDownloadManager extends DownloadManager {
	private final AtomicInteger completedDownloads = new AtomicInteger(0);
	private final Set<JdkMetadata.FileType> fileTypeFilter;
	private final ConcurrentHashMap<String, AtomicInteger> submittedPerDistro = new ConcurrentHashMap<>();

	private static final Logger logger = LoggerFactory.getLogger(NoOpDownloadManager.class);

	/**
	 * Create a new NoOpDownloadManager.
	 */
	public NoOpDownloadManager() {
		this(null);
	}

	/**
	 * Create a new NoOpDownloadManager.
	 *
	 * @param fileTypeFilter Set of file types to accept (null to accept all)
	 */
	public NoOpDownloadManager(Set<JdkMetadata.FileType> fileTypeFilter) {
		this.fileTypeFilter = fileTypeFilter;
	}

	/**
	 * Start the download manager. This is a no-op.
	 */
	@Override
	public void start() {
		logger.info("NoOpDownloadManager started - all downloads will be skipped");
	}

	/**
	 * Submit a metadata item for download. This implementation logs and ignores the request.
	 *
	 * @param metadata The JDK metadata containing the URL to download
	 * @param distro The distro name
	 * @param downloadLogger The logger for progress reporting
	 */
	@Override
	public void submit(JdkMetadata metadata, String distro, Logger downloadLogger) {
		if (metadata.getUrl() != null && metadata.getFilename() != null) {
			// Check file type filter
			if (fileTypeFilter != null && metadata.getFileType() != null) {
				try {
					if (!fileTypeFilter.contains(metadata.fileTypeEnum())) {
						logger.debug(
								"Ignoring download submission for {} [{}] - file type {} not in filter",
								metadata.getFilename(),
								distro,
								metadata.getFileType());
						return;
					}
				} catch (IllegalArgumentException e) {
					logger.debug(
							"Ignoring download submission for {} [{}] - unknown file type: {}",
							metadata.getFilename(),
							distro,
							metadata.getFileType());
					return;
				}
			}
			completedDownloads.incrementAndGet();
			submittedPerDistro
					.computeIfAbsent(distro, k -> new AtomicInteger(0))
					.incrementAndGet();
			logger.debug("Ignoring download request for: {} (no-download option specified)", metadata.getFilename());
			downloadLogger.debug(
					"Ignoring download request for: {} (no-download option specified)", metadata.getFilename());
		}
	}

	/**
	 * Signal that no more downloads will be submitted. This is a no-op.
	 */
	@Override
	public void shutdown() {
		logger.info("NoOpDownloadManager shutdown");
	}

	/**
	 * Wait for all queued downloads to complete. This returns immediately as there are no downloads.
	 */
	@Override
	public void awaitCompletion() {
		// No-op - nothing to wait for
	}

	/**
	 * Get the number of completed downloads.
	 *
	 * @return Returns the number of submitted downloads
	 */
	@Override
	public int getCompletedCount() {
		return completedDownloads.get();
	}

	/**
	 * Get the number of failed downloads.
	 *
	 * @return Always returns 0
	 */
	@Override
	public int getFailedCount() {
		return 0;
	}

	/**
	 * Get the number of downloads currently in progress.
	 *
	 * @return Always returns 0
	 */
	public int getActiveCount() {
		return 0;
	}

	/**
	 * Get the number of downloads waiting in the queue.
	 *
	 * @return Always returns 0
	 */
	public int getQueuedCount() {
		return 0;
	}

	/**
	 * No-op: NoOpDownloadManager never actually processes tasks.
	 */
	@Override
	protected void processDownload(DownloadTask task) {
		// No-op
	}

	/**
	 * Get per-distro download statistics.
	 *
	 * @return Map of distro name to statistics
	 */
	@Override
	public Map<String, DistroStats> getDistroStats() {
		Map<String, DistroStats> stats = new HashMap<>();
		for (Map.Entry<String, AtomicInteger> entry : submittedPerDistro.entrySet()) {
			String distro = entry.getKey();
			int submitted = entry.getValue().get();
			// In NoOp mode, all submitted items are considered "completed" (skipped)
			stats.put(distro, new DistroStats(distro, submitted, submitted, 0));
		}
		return stats;
	}
}
