package dev.jbang.jdkdb;

import dev.jbang.jdkdb.model.JdkMetadata;
import dev.jbang.jdkdb.scraper.DefaultDownloadManager;
import dev.jbang.jdkdb.scraper.DownloadManager;
import dev.jbang.jdkdb.scraper.InterruptedProgressException;
import dev.jbang.jdkdb.scraper.NoOpDownloadManager;
import dev.jbang.jdkdb.util.GitHubUtils;
import dev.jbang.jdkdb.util.HttpUtils;
import dev.jbang.jdkdb.util.MetadataUtils;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

/** Verify command to check that metadata download URLs are still valid */
@Command(
		name = "verify",
		description = "Verifies that metadata download URLs are still valid",
		mixinStandardHelpOptions = true)
public class VerifyCommand implements Callable<Integer> {
	private static final Logger logger = LoggerFactory.getLogger("command");

	@Option(
			names = {"-m", "--metadata-dir"},
			description = "Directory containing metadata files (default: db/metadata)",
			defaultValue = "db/metadata")
	private Path metadataDir;

	@Option(
			names = {"-c", "--checksum-dir"},
			description = "Directory to store checksum files (default: db/checksums)",
			defaultValue = "db/checksums")
	private Path checksumDir;

	@Option(
			names = {"-v", "--distros"},
			description =
					"Comma-separated list of distro names to process (if not specified, all distros are processed)",
			split = ",")
	private List<String> distroNames;

	@Option(
			names = {"-t", "--threads"},
			description = "Maximum number of parallel download threads (default: number of processors)",
			defaultValue = "-1")
	private int maxThreads;

	@Option(
			names = {"--limit-progress"},
			description =
					"Maximum number of metadata items to process per scraper before aborting (default: unlimited)",
			defaultValue = "-1")
	private int limitProgress;

	@Option(
			names = {"--limit-total"},
			description = "Maximum total number of downloads to accept before stopping (default: unlimited)",
			defaultValue = "-1")
	private int limitTotal;

	@Option(
			names = {"--stats-only"},
			description = "Skip downloading files and only show statistics (for testing/dry-run)")
	private boolean statsOnly;

	@Option(
			names = {"--include"},
			description =
					"Include only these file types (e.g., tar_gz,zip). If specified, only these types will be downloaded.",
			split = ",")
	private List<JdkMetadata.FileType> includeFileTypes;

	@Option(
			names = {"--exclude"},
			description = "Exclude these file types (e.g., msi,exe). These types will not be downloaded.",
			split = ",")
	private List<JdkMetadata.FileType> excludeFileTypes;

	@Option(
			names = {"--randomize"},
			description = "Randomize the order of downloads instead of processing files in order")
	private boolean randomize;

	@Option(
			names = {"--mark-missing"},
			description = "Mark files as missing_since when they return 403/404 during download")
	private boolean markMissing;

	@Override
	public Integer call() throws Exception {
		GitHubUtils.setupGitHubToken();

		// Process file type filter
		Set<JdkMetadata.FileType> fileTypeFilter =
				JdkMetadata.processFileTypeFilter(includeFileTypes, excludeFileTypes);

		logger.info("Java Metadata Scraper - Verify");
		logger.info("=================================");
		logger.info("Metadata directory: {}", metadataDir.toAbsolutePath());
		logger.info("Checksum directory: {}", checksumDir.toAbsolutePath());
		logger.info("");

		Path distroDir = metadataDir;
		if (!Files.exists(distroDir) || !Files.isDirectory(distroDir)) {
			logger.error("Error: Distro directory not found: {}", distroDir.toAbsolutePath());
			return 1;
		}

		// Determine which distros to process
		List<String> distrosToProcess;
		if (distroNames == null || distroNames.isEmpty()) {
			// Process all distros
			try (Stream<Path> paths = Files.list(distroDir)) {
				distrosToProcess = paths.filter(Files::isDirectory)
						.map(Path::getFileName)
						.map(Path::toString)
						.sorted()
						.toList();
			}
			logger.info("Processing all distros...");
		} else {
			distrosToProcess = distroNames;
			logger.info("Processing specified distros: {}", String.join(", ", distroNames));
		}
		logger.info("");

		// Create download manager
		var threadCount = maxThreads > 0 ? maxThreads : Runtime.getRuntime().availableProcessors();
		DownloadManager downloadManager = statsOnly
				? new NoOpDownloadManager(fileTypeFilter)
				: new VerifyDownloadManager(
						threadCount, 3, limitTotal, fileTypeFilter, new HttpUtils(), metadataDir, markMissing);
		downloadManager.start();
		if (fileTypeFilter != null) {
			logger.info("File type filter enabled: {}", fileTypeFilter);
		}

		List<JdkMetadata> metadataList = MetadataUtils.collectAllMetadata(distroDir, 2, true, false).stream()
				.filter(md -> md.getMissingSince() == null) // Don't process items marked as missing
				.filter(md -> md.getLastVerified()
						== null) // Don't process items that have been verified (TODO introduce flag to configure this)
				.collect(Collectors.toCollection(ArrayList::new));

		metadataList = prioritizeUnlistedEa(metadataList, randomize);
		if (randomize) {
			logger.info("Randomized download order (preserving unlisted-first priority)");
		}

		Map<String, Integer> distroMissingCounts = new HashMap<>();
		Set<String> distrosAtProgressLimit = new HashSet<>();
		for (JdkMetadata metadata : metadataList) {
			try {
				String distroName = metadata.getDistro();
				if (!distrosToProcess.contains(distroName)) {
					continue; // Skip distros not in the specified list
				}
				if (limitProgress > 0 && distrosAtProgressLimit.contains(distroName)) {
					continue;
				}
				Logger dl = LoggerFactory.getLogger("distros." + distroName);
				downloadManager.submit(metadata, distroName, dl);
				distroMissingCounts.put(distroName, distroMissingCounts.getOrDefault(distroName, 0) + 1);
				int distroMissing = distroMissingCounts.get(distroName);
				if (limitProgress > 0 && distroMissing >= limitProgress) {
					distrosAtProgressLimit.add(distroName);
					dl.info(
							"Reached progress limit of {} items for distro {}, skipping remaining files for this distro",
							limitProgress,
							distroName);
					logger.info(
							"Reached progress limit of {} items for distro {}, skipping remaining files for this distro",
							limitProgress,
							distroName);
					continue;
				}
			} catch (InterruptedProgressException e) {
				logger.info("Progress limit reached, stopping submission of new downloads");
				break;
			} catch (Exception e) {
				logger.error(
						"Failed to read metadata file: {} - {}",
						Path.of(metadata.getFilename()).getFileName(),
						e.getMessage());
			}
		}

		downloadManager.shutdown();

		int filesWithMissingData = metadataList.size();
		int totalCompleted = 0;
		int totalFailed = 0;

		logger.info("Waiting for downloads to complete...");
		try {
			downloadManager.awaitCompletion();
			totalCompleted = downloadManager.getCompletedCount();
			totalFailed = downloadManager.getFailedCount();
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			logger.error("Download manager interrupted while waiting for completion");
			return 1;
		}

		logger.info("Summary");
		logger.info("=======");
		logger.info("Items pending verification (no last_verified): {}", filesWithMissingData);
		if (filesWithMissingData > 0) {
			// Per-distro breakdown
			Map<String, ? extends DownloadManager.DistroStats> distroStats = downloadManager.getDistroStats();
			if (!distroStats.isEmpty()) {
				logger.info("Per-Distro Breakdown");
				logger.info("====================");
				distroStats.entrySet().stream()
						.sorted(Map.Entry.comparingByKey())
						.forEach(entry -> {
							DownloadManager.DistroStats stats = entry.getValue();
							logger.info("  {}:", stats.distro());
							logger.info("    Submitted:        {}", stats.submitted());
							logger.info("    Completed:        {}", stats.completed());
							logger.info("    Failed:           {}", stats.failed());
							if (stats.pending() > 0) {
								logger.info("    Pending:          {}", stats.pending());
							}
							if (stats instanceof VerifyDistroStats vs) {
								logger.info("    Verified:         {}", vs.verifiedCount());
								logger.info("    Marked missing:   {}", vs.markedMissingCount());
							}
						});
			}

			logger.info("");
			logger.info("Total downloads completed: {}", totalCompleted);
			logger.info("Total downloads failed: {}", totalFailed);
			if (downloadManager instanceof VerifyDownloadManager vm) {
				logger.info("Items marked last_verified: {}", vm.getTotalVerifiedCount());
				logger.info("Items marked missing_since: {}", vm.getTotalMarkedMissingCount());
			}
		}

		return totalCompleted > 0 ? 0 : 1;
	}

	static List<JdkMetadata> prioritizeUnlistedEa(List<JdkMetadata> metadataList, boolean randomize) {
		List<JdkMetadata> ordered = new ArrayList<>(metadataList.size());
		ordered.addAll(metadataList);
		Comparator<JdkMetadata> priorityComparator = Comparator.comparing((metadata) -> !hasUnlistedSince(metadata));
		priorityComparator =
				priorityComparator.thenComparing(metadata -> "ea".equalsIgnoreCase(metadata.getReleaseType()) ? 0 : 1);
		ordered.sort(priorityComparator);

		if (randomize) {
			int groupStart = 0;
			while (groupStart < ordered.size()) {
				int groupEnd = groupStart + 1;
				while (groupEnd < ordered.size()
						&& priorityComparator.compare(ordered.get(groupStart), ordered.get(groupEnd)) == 0) {
					groupEnd++;
				}
				Collections.shuffle(ordered.subList(groupStart, groupEnd));
				groupStart = groupEnd;
			}
		}
		return ordered;
	}

	private static boolean hasUnlistedSince(JdkMetadata metadata) {
		String unlistedSince = metadata.getUnlistedSince();
		return unlistedSince != null && !unlistedSince.isBlank();
	}

	// -------------------------------------------------------------------------
	// VerifyDistroStats
	// -------------------------------------------------------------------------

	/**
	 * Extended per-distro stats produced by {@link VerifyDownloadManager}. Carries
	 * verification-specific fields on top of the base counts.
	 */
	public static class VerifyDistroStats extends DownloadManager.DistroStats {
		private final int verifiedCount;
		private final int markedMissingCount;

		public VerifyDistroStats(
				String distro, int submitted, int completed, int failed, int verifiedCount, int markedMissingCount) {
			super(distro, submitted, completed, failed);
			this.verifiedCount = verifiedCount;
			this.markedMissingCount = markedMissingCount;
		}

		/** Number of items successfully verified (HTTP 2xx) for this distro. */
		public int verifiedCount() {
			return verifiedCount;
		}

		/** Number of items marked as missing_since for this distro. */
		public int markedMissingCount() {
			return markedMissingCount;
		}
	}

	// -------------------------------------------------------------------------
	// VerifyDownloadManager
	// -------------------------------------------------------------------------

	/**
	 * Concrete {@link DefaultDownloadManager} for the verify command. Implements
	 * {@link #processDownload} with URL-check logic and overrides
	 * {@link #createDistroStats} to return {@link VerifyDistroStats}.
	 */
	public static class VerifyDownloadManager extends DefaultDownloadManager {
		private final HttpUtils httpUtils;
		private final Path metadataDir;
		private final boolean markMissing;

		// Per-distro tracking
		private final ConcurrentHashMap<String, AtomicInteger> verifiedPerDistro = new ConcurrentHashMap<>();
		private final ConcurrentHashMap<String, AtomicInteger> markedMissingPerDistro = new ConcurrentHashMap<>();

		public VerifyDownloadManager(
				int threadCount,
				int maxDownloadsPerHost,
				int limitTotal,
				Set<JdkMetadata.FileType> fileTypeFilter,
				HttpUtils httpUtils,
				Path metadataDir,
				boolean markMissing) {
			super(threadCount, maxDownloadsPerHost, limitTotal, fileTypeFilter);
			this.httpUtils = httpUtils;
			this.metadataDir = metadataDir;
			this.markMissing = markMissing;
		}

		/** Total verified count across all distros. */
		public int getTotalVerifiedCount() {
			return verifiedPerDistro.values().stream()
					.mapToInt(AtomicInteger::get)
					.sum();
		}

		/** Total marked-missing count across all distros. */
		public int getTotalMarkedMissingCount() {
			return markedMissingPerDistro.values().stream()
					.mapToInt(AtomicInteger::get)
					.sum();
		}

		@Override
		protected void processDownload(DownloadTask task) throws IOException, InterruptedException {
			JdkMetadata metadata = task.metadata();
			String filename = metadata.getFilename();
			String url = metadata.getUrl();
			String today = java.time.LocalDate.now().toString();

			if (filename == null || url == null) {
				return;
			}

			if (!metadata.isValid()) {
				task.downloadLogger().warn("Skipping invalid metadata for: {}", filename);
				return;
			}

			if (metadata.getUnlistedSince() != null) {
				task.downloadLogger()
						.info(
								"Metadata item {} has unlisted_since={} - attempting download",
								filename,
								metadata.getUnlistedSince());
			}
			task.downloadLogger().info("Downloading " + filename);
			int status = httpUtils.urlStatus(url);
			if (status >= 200 && status < 300) {
				if (markMissing && metadata.getMissingSince() != null) {
					task.downloadLogger().info("Clearing missing_since for {}", filename);
					metadata.setMissingSince(null);
				}
				metadata.setLastVerified(today);
				saveMetadata(task, metadataDir, metadata);

				verifiedPerDistro
						.computeIfAbsent(task.distro(), k -> new AtomicInteger(0))
						.incrementAndGet();

				// Report success
				task.downloadLogger().info("Verification successful for {}", filename);
			} else if (status == 403 || status == 404) {
				if (metadata.getUnlistedSince() != null) {
					task.downloadLogger()
							.warn(
									"Download returned 40X for unlisted package {} (unlisted_since={}). "
											+ "This package is most likely not available anymore and is a candidate for pruning.",
									filename,
									metadata.getUnlistedSince());
				}
				if (markMissing && metadata.getMissingSince() == null) {
					metadata.setMissingSince(today);
					task.downloadLogger().warn("Marking {} as missing_since={}", filename, today);
					saveMetadata(task, metadataDir, metadata);

					markedMissingPerDistro
							.computeIfAbsent(task.distro(), k -> new AtomicInteger(0))
							.incrementAndGet();
				}
			} else {
				throw new IOException("Download failed with HTTP status: " + status);
			}
		}

		@Override
		protected DistroStats createDistroStats(String distro, int submitted, int completed, int failed) {
			int verified =
					verifiedPerDistro.getOrDefault(distro, new AtomicInteger(0)).get();
			int markedMissing = markedMissingPerDistro
					.getOrDefault(distro, new AtomicInteger(0))
					.get();
			return new VerifyDistroStats(distro, submitted, completed, failed, verified, markedMissing);
		}

		private static Path saveMetadata(DownloadTask task, Path metadataDir, JdkMetadata metadata) throws IOException {
			Path distroMetadataDir = metadataDir.resolve(task.distro());
			Files.createDirectories(distroMetadataDir);
			Path metadataFile = distroMetadataDir.resolve(metadata.metadataFile());
			MetadataUtils.saveMetadataFile(metadataFile, metadata);
			return metadataFile;
		}
	}
}
