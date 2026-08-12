package dev.jbang.jdkdb;

import dev.jbang.jdkdb.model.JdkMetadata;
import dev.jbang.jdkdb.scraper.DefaultDownloadManager;
import dev.jbang.jdkdb.scraper.DownloadManager;
import dev.jbang.jdkdb.scraper.InterruptedProgressException;
import dev.jbang.jdkdb.scraper.NoOpDownloadManager;
import dev.jbang.jdkdb.util.ArchiveUtils;
import dev.jbang.jdkdb.util.GitHubUtils;
import dev.jbang.jdkdb.util.MetadataUtils;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

/** Download command to download missing checksums for existing metadata files */
@Command(
		name = "download",
		description = "Download and compute checksums for metadata files that have missing checksum values",
		mixinStandardHelpOptions = true)
public class DownloadCommand implements Callable<Integer> {
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
		Set<JdkMetadata.FileType> fileTypeFilter = processFileTypeFilter(includeFileTypes, excludeFileTypes);

		logger.info("Java Metadata Scraper - Download");
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
				: new DefaultDownloadManager(
						threadCount, metadataDir, checksumDir, 3, limitTotal, fileTypeFilter, markMissing);
		downloadManager.start();
		if (fileTypeFilter != null) {
			logger.info("File type filter enabled: {}", fileTypeFilter);
		}

		List<JdkMetadata> metadataList = MetadataUtils.collectAllMetadata(distroDir, 2, false, true).stream()
				// Don't try to download macOS PKG files if the only thing we need is
				// the release info, since we won't be able to extract it on non-macOS
				// platforms anyway!
				.filter(m -> !"macosx".equals(m.getOs())
						|| !MetadataUtils.MACOS_FILE_TYPES.contains(m.fileTypeEnum())
						|| MetadataUtils.hasMissingChecksums(m)
						|| !MetadataUtils.hasMissingReleaseInfo(m)
						|| ArchiveUtils.isMacOS())
				// The same goes for Windows EXE files - but in this case we always
				// ignore missing release info since we can't extract it from them
				// on any platform!
				.filter(m -> !"windows".equals(m.getOs())
						|| !"exe".equals(m.getFileType())
						|| MetadataUtils.hasMissingChecksums(m)
						|| !MetadataUtils.hasMissingReleaseInfo(m))
				.collect(Collectors.toCollection(ArrayList::new));

		metadataList = prioritizeMetadata(metadataList, randomize);
		if (randomize) {
			logger.info("Randomized download order (preserving checksum-first priority)");
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
		logger.info("Files with missing data: {}", filesWithMissingData);
		if (filesWithMissingData > 0) {
			// Per-distro breakdown
			Map<String, DownloadManager.DistroStats> distroStats = downloadManager.getDistroStats();
			if (!distroStats.isEmpty()) {
				logger.info("Per-Distro Breakdown");
				logger.info("====================");
				distroStats.entrySet().stream()
						.sorted(Map.Entry.comparingByKey())
						.forEach(entry -> {
							DownloadManager.DistroStats stats = entry.getValue();
							logger.info("  {}:", stats.distro());
							logger.info("    Submitted:  {}", stats.submitted());
							logger.info("    Completed:  {}", stats.completed());
							logger.info("    Failed:     {}", stats.failed());
							if (stats.pending() > 0) {
								logger.info("    Pending:    {}", stats.pending());
							}
						});
			}

			int filesWithMissingChecksums = (int) metadataList.stream()
					.filter(MetadataUtils::hasMissingChecksums)
					.count();
			int filesWithMissingReleaseInfo = (int) metadataList.stream()
					.filter(m -> MetadataUtils.hasMissingReleaseInfo(m))
					.count();
			logger.info("");
			logger.info("Files with missing checksums: {}", filesWithMissingChecksums);
			logger.info("Files with missing release info: {}", filesWithMissingReleaseInfo);
			logger.info("Total downloads completed: {}", totalCompleted);
			logger.info("Total downloads failed: {}", totalFailed);
		}

		return totalCompleted > 0 ? 0 : 1;
	}

	static List<JdkMetadata> prioritizeMetadata(List<JdkMetadata> metadataList, boolean randomize) {
		List<JdkMetadata> missingChecksums = metadataList.stream()
				.filter(MetadataUtils::hasMissingChecksums)
				.collect(Collectors.toCollection(ArrayList::new));
		List<JdkMetadata> missingReleaseInfoOnly = metadataList.stream()
				.filter(m -> !MetadataUtils.hasMissingChecksums(m) && MetadataUtils.hasMissingReleaseInfo(m))
				.collect(Collectors.toCollection(ArrayList::new));

		if (randomize) {
			Collections.shuffle(missingChecksums);
			Collections.shuffle(missingReleaseInfoOnly);
		}

		List<JdkMetadata> ordered = new ArrayList<>(metadataList.size());
		ordered.addAll(missingChecksums);
		ordered.addAll(missingReleaseInfoOnly);
		return ordered;
	}

	/**
	 * Process the include and exclude file type options to create a filter set.
	 *
	 * @param includeFileTypes List of file types to include (null or empty means include all)
	 * @param excludeFileTypes List of file types to exclude (null or empty means exclude none)
	 * @return A set of file types to accept, or null if no filtering should be applied
	 */
	private Set<JdkMetadata.FileType> processFileTypeFilter(
			List<JdkMetadata.FileType> includeFileTypes, List<JdkMetadata.FileType> excludeFileTypes) {
		if ((includeFileTypes == null || includeFileTypes.isEmpty())
				&& (excludeFileTypes == null || excludeFileTypes.isEmpty())) {
			return null; // No filtering
		}

		Set<JdkMetadata.FileType> result;
		if (includeFileTypes != null && !includeFileTypes.isEmpty()) {
			// Start with only the included types
			result = EnumSet.copyOf(includeFileTypes);
		} else {
			// Start with all types
			result = EnumSet.allOf(JdkMetadata.FileType.class);
		}

		// Remove excluded types
		if (excludeFileTypes != null && !excludeFileTypes.isEmpty()) {
			result.removeAll(excludeFileTypes);
		}

		return result.isEmpty() ? null : result;
	}
}
