package dev.jbang.jdkdb.scraper;

import dev.jbang.jdkdb.model.JdkMetadata;
import java.io.*;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Default implementation that manages parallel downloads of JDK files across multiple threads.
 * Receives JdkMetadata from scrapers, queues them, and downloads files in parallel worker threads.
 */
public class DefaultDownloadManager implements DownloadManager {
	private final BlockingQueue<DownloadTask> downloadQueue;
	private final ExecutorService executorService;
	private final AtomicInteger activeDownloads;
	private final AtomicInteger completedDownloads;
	private final AtomicInteger failedDownloads;
	private final AtomicInteger submittedCount;
	private volatile boolean shutdownRequested;
	private final int maxDownloadsPerHost;
	private final int limitTotal;
	private final DownloadProcessor downloadProcessor;
	private final ConcurrentHashMap<String, AtomicInteger> activeDownloadsPerHost;
	private final Set<JdkMetadata.FileType> fileTypeFilter;
	private final ConcurrentHashMap<String, AtomicInteger> submittedPerDistro;
	private final ConcurrentHashMap<String, AtomicInteger> completedPerDistro;
	private final ConcurrentHashMap<String, AtomicInteger> failedPerDistro;
	private static final Logger logger = LoggerFactory.getLogger(DefaultDownloadManager.class);

	@FunctionalInterface
	public static interface DownloadProcessor {
		void processDownload(DownloadTask task) throws IOException, InterruptedException;
	}

	/**
	 * Create a new DefaultDownloadManager.
	 *
	 * @param threadCount Number of parallel download threads
	 * @param maxDownloadsPerHost Maximum number of concurrent downloads per host (default: 3)
	 * @param limitTotal Maximum number of total downloads to accept (-1 for unlimited)
	 * @param fileTypeFilter Set of file types to accept (null to accept all)
	 * @param downloadProcessor Download processor to handle the actual download logic
	 */
	public DefaultDownloadManager(
			int threadCount,
			int maxDownloadsPerHost,
			int limitTotal,
			Set<JdkMetadata.FileType> fileTypeFilter,
			DownloadProcessor downloadProcessor) {
		this.downloadQueue = new LinkedBlockingQueue<>();
		this.executorService = Executors.newFixedThreadPool(threadCount);
		this.activeDownloads = new AtomicInteger(0);
		this.completedDownloads = new AtomicInteger(0);
		this.failedDownloads = new AtomicInteger(0);
		this.submittedCount = new AtomicInteger(0);
		this.shutdownRequested = false;
		this.maxDownloadsPerHost = maxDownloadsPerHost;
		this.limitTotal = limitTotal;
		this.activeDownloadsPerHost = new ConcurrentHashMap<>();
		this.fileTypeFilter = fileTypeFilter;
		this.submittedPerDistro = new ConcurrentHashMap<>();
		this.completedPerDistro = new ConcurrentHashMap<>();
		this.failedPerDistro = new ConcurrentHashMap<>();
		this.downloadProcessor = downloadProcessor;
	}

	/**
	 * Start the download worker threads. Should be called once after construction.
	 */
	@Override
	public void start() {
		logger.info(
				"Starting DownloadManager with {} threads, max {} downloads per host",
				((ThreadPoolExecutor) executorService).getCorePoolSize(),
				maxDownloadsPerHost);
		int threadCount = ((ThreadPoolExecutor) executorService).getCorePoolSize();
		for (int i = 0; i < threadCount; i++) {
			executorService.submit(this::downloadWorker);
		}
	}

	/**
	 * Submit a metadata item for download.
	 *
	 * @param metadata The JDK metadata containing the URL to download
	 * @param distro The distro of the JDK
	 * @param downloadLogger The logger for progress reporting
	 */
	@Override
	public void submit(JdkMetadata metadata, String distro, Logger downloadLogger) {
		if (shutdownRequested) {
			throw new IllegalStateException("Cannot submit downloads after shutdown requested");
		}
		if (metadata.getUrl() == null || metadata.getFilename() == null) {
			return;
		}
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
		// Check if we've reached the total download limit
		if (limitTotal > 0) {
			int currentCount = submittedCount.incrementAndGet();
			if (currentCount > limitTotal) {
				throw new InterruptedProgressException("Reached total download limit of " + limitTotal + " items");
			}
		}
		// Track submitted downloads per distro
		submittedPerDistro.computeIfAbsent(distro, k -> new AtomicInteger(0)).incrementAndGet();
		try {
			downloadQueue.put(new DownloadTask(metadata, distro, downloadLogger));
			downloadLogger.info("Queued download for " + metadata.getFilename());
			logger.debug("Submitted download for {} - {}", distro, metadata.getFilename());
			logger.info(
					"Downloads: {} queued, {} active, {} completed, {} failed",
					downloadQueue.size(),
					activeDownloads.get(),
					completedDownloads.get(),
					failedDownloads.get());
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			throw new RuntimeException("Interrupted while submitting download", e);
		}
	}

	/**
	 * Signal that no more downloads will be submitted. Call this after all scrapers have finished.
	 */
	@Override
	public void shutdown() {
		logger.info("Shutting down DownloadManager");
		shutdownRequested = true;
	}

	/**
	 * Wait for all queued downloads to complete. This method blocks until all downloads are
	 * finished.
	 *
	 * @throws InterruptedException if interrupted while waiting
	 */
	@Override
	public void awaitCompletion() throws InterruptedException {
		// Wait for queue to be empty and all downloads to complete
		while (!downloadQueue.isEmpty() || activeDownloads.get() > 0) {
			Thread.sleep(100);
		}

		// Shutdown executor and wait for all threads to finish
		executorService.shutdown();
		executorService.awaitTermination(1, TimeUnit.HOURS);
	}

	/**
	 * Get the number of completed downloads.
	 *
	 * @return Number of successfully completed downloads
	 */
	@Override
	public int getCompletedCount() {
		return completedDownloads.get();
	}

	/**
	 * Get the number of failed downloads.
	 *
	 * @return Number of failed downloads
	 */
	@Override
	public int getFailedCount() {
		return failedDownloads.get();
	}

	/**
	 * Get the number of downloads currently in progress.
	 *
	 * @return Number of active downloads
	 */
	public int getActiveCount() {
		return activeDownloads.get();
	}

	/**
	 * Get the number of downloads waiting in the queue.
	 *
	 * @return Number of queued downloads
	 */
	public int getQueuedCount() {
		return downloadQueue.size();
	}

	/** Worker thread that processes downloads from the queue */
	private void downloadWorker() {
		while (!shutdownRequested || !downloadQueue.isEmpty()) {
			try {
				DownloadTask task = takeNewDownloadTask();
				if (task != null) {
					setupNewDownload(task);
					logger.info(
							"Downloads: {} queued, {} active, {} completed, {} failed",
							downloadQueue.size(),
							activeDownloads.get(),
							completedDownloads.get(),
							failedDownloads.get());
				}
			} catch (InterruptedException e) {
				Thread.currentThread().interrupt();
				break;
			} catch (Throwable t) {
				logger.error("Unexpected error in download worker thread", t);
				// Continue processing other downloads despite the error
			}
		}
	}

	private DownloadTask takeNewDownloadTask() throws InterruptedException {
		return downloadQueue.poll(500, TimeUnit.MILLISECONDS);
	}

	private void setupNewDownload(DownloadTask task) throws InterruptedException {
		// Extract host from URL
		String host = extractHost(task.metadata.getUrl());
		if (host == null) {
			// Invalid URL, log failure and skip
			failedDownloads.incrementAndGet();
			task.downloadLogger().error("Invalid URL for {}: {}", task.metadata.getFilename(), task.metadata.getUrl());
			logger.debug("Failed download for {} [{}] - invalid URL", task.metadata.getFilename(), task.distro);
			return;
		}

		// Check if we can download from this host
		AtomicInteger hostCount = activeDownloadsPerHost.computeIfAbsent(host, k -> new AtomicInteger(0));

		if (!tryAcquireHostSlot(hostCount)) {
			// Host limit reached, put task back at end of queue and try next one
			downloadQueue.offer(task);
			// Small sleep to avoid busy waiting when all hosts are at limit
			Thread.sleep(100);
			return;
		}

		// Host slot acquired, proceed with download
		activeDownloads.incrementAndGet();
		try {
			downloadProcessor.processDownload(task);
			completedDownloads.incrementAndGet();
			completedPerDistro
					.computeIfAbsent(task.distro, k -> new AtomicInteger(0))
					.incrementAndGet();
			logger.debug("Succeeded download for {} [{}]", task.metadata.getFilename(), task.distro);
		} catch (Throwable t) {
			failedDownloads.incrementAndGet();
			failedPerDistro
					.computeIfAbsent(task.distro, k -> new AtomicInteger(0))
					.incrementAndGet();
			task.downloadLogger().error("Failed to download {}", task.metadata.getFilename(), t);
			logger.debug("Failed download for {} [{}]", task.metadata.getFilename(), task.distro);
		} finally {
			activeDownloads.decrementAndGet();
			// Decrement host counter
			int newCount = hostCount.decrementAndGet();
			// Clean up if no more active downloads for this host
			if (newCount == 0) {
				activeDownloadsPerHost.remove(host, hostCount);
			}
		}
	}

	private boolean tryAcquireHostSlot(AtomicInteger hostCount) {
		while (true) {
			int current = hostCount.get();
			if (current >= maxDownloadsPerHost) {
				return false;
			}
			if (hostCount.compareAndSet(current, current + 1)) {
				return true;
			}
		}
	}

	/**
	 * Extract the host from a URL.
	 *
	 * @param urlString The URL string
	 * @return The host, or null if the URL is invalid
	 */
	private static String extractHost(String urlString) {
		if (urlString == null) {
			return null;
		}
		try {
			URI uri = new URI(urlString);
			return uri.getHost();
		} catch (URISyntaxException e) {
			logger.warn("Invalid URL: {}", urlString);
			return null;
		}
	}

	/**
	 * Get per-distro download statistics.
	 *
	 * @return Map of distro name to statistics
	 */
	@Override
	public Map<String, DistroStats> getDistroStats() {
		Map<String, DistroStats> stats = new HashMap<>();
		// Get all distro names from any of the maps
		Set<String> allDistros = new HashSet<>();
		allDistros.addAll(submittedPerDistro.keySet());
		allDistros.addAll(completedPerDistro.keySet());
		allDistros.addAll(failedPerDistro.keySet());

		for (String distro : allDistros) {
			int submitted = submittedPerDistro
					.getOrDefault(distro, new AtomicInteger(0))
					.get();
			int completed = completedPerDistro
					.getOrDefault(distro, new AtomicInteger(0))
					.get();
			int failed =
					failedPerDistro.getOrDefault(distro, new AtomicInteger(0)).get();
			stats.put(distro, new DistroStats(distro, submitted, completed, failed));
		}
		return stats;
	}

	/** Internal class representing a download task */
	public static record DownloadTask(JdkMetadata metadata, String distro, Logger downloadLogger) {}
}
