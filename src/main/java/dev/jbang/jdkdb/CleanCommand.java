package dev.jbang.jdkdb;

import dev.jbang.jdkdb.model.JdkMetadata;
import dev.jbang.jdkdb.util.MetadataUtils;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.FileTime;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

/** Clean command to remove incomplete metadata files and prune old EA releases */
@Command(
		name = "clean",
		description = "Clean up metadata by removing incomplete files and pruning old EA releases",
		mixinStandardHelpOptions = true)
public class CleanCommand implements Callable<Integer> {
	private static final Logger logger = LoggerFactory.getLogger("command");

	/** Enum for incomplete metadata types */
	public enum IncompleteType {
		checksums,
		release_info,
		all
	}

	@Option(
			names = {"-m", "--metadata-dir"},
			description = "Directory containing metadata files (default: db/metadata)",
			defaultValue = "db/metadata")
	private Path metadataDir;

	@Option(
			names = {"-c", "--checksum-dir"},
			description = "Directory containing checksum files (default: db/checksums)",
			defaultValue = "db/checksums")
	private Path checksumDir;

	@Option(
			names = {"--remove-incomplete"},
			description =
					"Remove metadata files with incomplete data. Options: checksums (missing checksums), release-info (missing release info), all (either missing checksums or release info) (default: all)")
	private IncompleteType removeIncomplete;

	@Option(
			names = {"--remove-invalid"},
			description = "Remove metadata files that fail validation (MetadataUtils.isValidMetadata)")
	private boolean removeInvalid;

	@Option(
			names = {"--remove-orphaned"},
			description = "Remove orphaned checksum files that don't have a matching metadata file")
	private boolean removeOrphanedChecksums;

	@Option(
			names = {"--prune-ea"},
			arity = "0..1",
			paramLabel = "SINCE",
			description =
					"Prune EA releases older than specified duration (e.g., 30d, 3w, 6m, 1y). Duration format: [number][d|w|m|y]")
	private String pruneEa;

	@Option(
			names = {"--prune-unlisted"},
			arity = "0..1",
			paramLabel = "SINCE",
			description =
					"Prune metadata files with unlisted_since up to SINCE. SINCE uses duration format [number][d|w|m|y].")
	private String pruneUnlisted;

	@Option(
			names = {"-p", "--prune-dir"},
			description = "Directory to move pruned files into (default: db/pruned)",
			defaultValue = "db/pruned")
	private Path pruneDir;

	@Option(
			names = {"--dry-run"},
			description = "Show statistics without actually deleting files")
	private boolean dryRun;

	@Override
	public Integer call() throws Exception {
		logger.info("Java Metadata Scraper - Clean");
		logger.info("=============================");
		logger.info("Metadata directory: {}", metadataDir.toAbsolutePath());
		logger.info("Checksum directory: {}", checksumDir.toAbsolutePath());
		logger.info("Prune directory: {}", pruneDir.toAbsolutePath());

		// Apply default values if no options specified
		if (removeIncomplete == null
				&& !removeInvalid
				&& pruneEa == null
				&& pruneUnlisted == null
				&& !removeOrphanedChecksums
				&& !dryRun) {
			logger.info("No options specified, using defaults: --remove-incomplete=all --prune-ea=6m --dry-run");
			logger.info("");
			removeIncomplete = IncompleteType.all;
			removeInvalid = true;
			removeOrphanedChecksums = true;
			pruneEa = "6m";
			pruneUnlisted = "1w";
			dryRun = true;
		}

		logger.info("Configuration:");
		logger.info(
				"  Remove incomplete: {}",
				(removeIncomplete != null
						? removeIncomplete.toString().toLowerCase().replace("_", "-")
						: "disabled"));
		logger.info("  Remove invalid: {}", removeInvalid);
		logger.info("  Prune EA: {}", (pruneEa != null ? pruneEa : "disabled"));
		logger.info("  Prune unlisted: {}", (pruneUnlisted != null ? pruneUnlisted : "disabled"));
		logger.info("  Remove orphaned checksums: {}", removeOrphanedChecksums);
		logger.info("  Dry run: {}", dryRun);
		logger.info("");

		Path distroDir = metadataDir;
		if (!Files.exists(distroDir) || !Files.isDirectory(distroDir)) {
			logger.error("Error: Distro directory not found: {}", distroDir.toAbsolutePath());
			return 1;
		}

		// Parse prune-ea duration if specified
		Instant pruneEaThreshold = null;
		if (pruneEa != null) {
			Duration duration = MetadataUtils.parseDuration(pruneEa);
			if (duration == null) {
				logger.error("Error: Invalid duration format: {}", pruneEa);
				logger.error("Expected format: [number][d|w|m|y] (e.g., 30d, 3w, 6m, 1y)");
				return 1;
			}
			pruneEaThreshold = Instant.now().minus(duration);
			logger.info("Pruning EA releases older than: {} (before {})", pruneEa, pruneEaThreshold);
			logger.info("");
		}

		Instant pruneUnlistedThreshold = null;
		if (pruneUnlisted != null) {
			Duration duration = MetadataUtils.parseDuration(pruneUnlisted);
			if (duration == null) {
				logger.error("Error: Invalid --prune-unlisted SINCE value: {}", pruneUnlisted);
				logger.error("Expected format: [number][d|w|m|y] (e.g., 30d, 3w, 6m, 1y)");
				return 1;
			}
			pruneUnlistedThreshold = Instant.now().minus(duration);
			logger.info("Pruning unlisted metadata up to: {} (<= {})", pruneUnlisted, pruneUnlistedThreshold);
			logger.info("");
		}

		// Collect files to delete/prune
		final CleanStats stats = new CleanStats();
		final List<Path> filesToDelete = new ArrayList<>();
		final List<Path> filesToPrune = new ArrayList<>();
		final Instant finalPruneEaThreshold = pruneEaThreshold;
		final Instant finalPruneUnlistedThreshold = pruneUnlistedThreshold;

		List<JdkMetadata> metadataList = MetadataUtils.collectAllMetadata(distroDir, 2, true, true);
		for (JdkMetadata metadata : metadataList) {
			try {
				processMetadataFile(
						metadata,
						stats,
						filesToDelete,
						filesToPrune,
						finalPruneEaThreshold,
						finalPruneUnlistedThreshold);
			} catch (IOException e) {
				logger.error("Failed to process {}: {}", metadata.metadataFile().getFileName(), e.getMessage());
				stats.errors++;
			}
		}

		// Remove orphaned checksum files (runs last after metadata cleanup)
		if (removeOrphanedChecksums) {
			logger.info("Removing orphaned checksum files...");
			logger.info("");
			removeOrphanedChecksums(distroDir, stats, filesToDelete);
		}

		// Print summary
		logger.info("");
		logger.info("Summary:");
		logger.info("========");
		logger.info("Total files scanned: {}", stats.totalFiles);
		logger.info("Incomplete files (total): {}", stats.incompleteChecksums + stats.incompleteReleaseInfo);
		logger.info("   - missing checksums): {}", stats.incompleteChecksums);
		logger.info("   - missing release info): {}", stats.incompleteReleaseInfo);
		logger.info("Invalid files: {}", stats.invalidFiles);
		logger.info("Old EA releases: {}", stats.oldEaReleases);
		logger.info("Unlisted releases: {}", stats.unlistedReleases);
		logger.info("Orphaned checksum files: {}", stats.orphanedChecksums);
		logger.info("Errors: {}", stats.errors);
		logger.info("");

		if (filesToDelete.isEmpty() && filesToPrune.isEmpty()) {
			logger.info("No files to delete or prune.");
			return 0;
		}

		logger.info("Files to prune: {}", filesToPrune.size());
		logger.info("Files to delete: {}", filesToDelete.size());

		if (dryRun) {
			logger.info("");
			logger.info("DRY RUN - No files were actually pruned or deleted.");
			logger.info("Run without --dry-run to perform actual prune/delete operations.");
		} else {
			logger.info("");
			logger.info("Pruning files...");
			int prunedCount = 0;
			int pruneFailedCount = 0;

			for (Path file : filesToPrune) {
				try {
					Path target = resolvePruneTarget(file);
					if (target == null) {
						logger.error("  Failed to prune {}: unable to resolve prune target", file.getFileName());
						pruneFailedCount++;
						continue;
					}
					Files.createDirectories(target.getParent());
					Files.move(file, target, StandardCopyOption.REPLACE_EXISTING);
					prunedCount++;
					logger.info("  Pruned: {}", file.getFileName());
				} catch (IOException e) {
					logger.error("  Failed to prune {}: {}", file.getFileName(), e.getMessage());
					pruneFailedCount++;
				}
			}

			logger.info("");
			logger.info("Deleting files...");
			int deletedCount = 0;
			int failedCount = 0;

			for (Path file : filesToDelete) {
				try {
					Files.delete(file);
					deletedCount++;
					logger.info("  Deleted: {}", file.getFileName());
				} catch (IOException e) {
					logger.error("  Failed to delete {}: {}", file.getFileName(), e.getMessage());
					failedCount++;
				}
			}

			logger.info("");
			logger.info("Pruned: {} files", prunedCount);
			if (pruneFailedCount > 0) {
				logger.info("Failed to prune: {} files", pruneFailedCount);
			}
			logger.info("Deleted: {} files", deletedCount);
			if (failedCount > 0) {
				logger.info("Failed to delete: {} files", failedCount);
			}
		}

		return 0;
	}

	private void processMetadataFile(
			JdkMetadata metadata,
			CleanStats stats,
			List<Path> filesToDelete,
			List<Path> filesToPrune,
			Instant pruneEaThreshold,
			Instant pruneUnlistedThreshold)
			throws IOException {
		stats.totalFiles++;

		Path metadataFile = metadata.metadataFile();

		boolean shouldDelete = false;
		boolean shouldPrune = false;
		String reason = null;

		// Check for invalid metadata
		if (removeInvalid && !shouldDelete) {
			if (!MetadataUtils.isValidMetadata(metadata)) {
				stats.invalidFiles++;
				shouldDelete = true;
				reason = "invalid (failed validation)";
			}
		}

		// Check for incomplete metadata
		if (removeIncomplete != null && !shouldDelete) {
			boolean missingChecksums = metadata.getMd5() == null
					|| metadata.getSha1() == null
					|| metadata.getSha256() == null
					|| metadata.getSha512() == null;
			boolean missingReleaseInfo = metadata.getReleaseInfo() == null;

			boolean isIncomplete =
					switch (removeIncomplete) {
						case checksums -> missingChecksums;
						case release_info -> missingReleaseInfo;
						case all -> missingChecksums || missingReleaseInfo;
					};

			if (isIncomplete) {
				shouldDelete = true;
				if (removeIncomplete == IncompleteType.checksums || (missingChecksums && !missingReleaseInfo)) {
					reason = "incomplete - missing checksums";
					stats.incompleteChecksums++;
				} else if (removeIncomplete == IncompleteType.release_info
						|| (missingReleaseInfo && !missingChecksums)) {
					reason = "incomplete - missing release info";
					stats.incompleteReleaseInfo++;
				} else {
					reason = "incomplete - missing checksums and release info";
					stats.incompleteChecksums++;
					stats.incompleteReleaseInfo++;
				}
			}
		}

		// Check for old EA releases
		if (pruneEaThreshold != null && "ea".equalsIgnoreCase(metadata.getReleaseType()) && !shouldDelete) {
			FileTime lastModified = Files.getLastModifiedTime(metadataFile);
			if (lastModified.toInstant().isBefore(pruneEaThreshold)) {
				stats.oldEaReleases++;
				shouldPrune = true;
				reason = "old EA release (last modified: " + lastModified.toInstant() + ")";
			}
		}

		if (pruneUnlistedThreshold != null && !shouldDelete) {
			String unlistedSince = metadata.getUnlistedSince();
			if (matchesUnlistedSince(unlistedSince, pruneUnlistedThreshold)) {
				stats.unlistedReleases++;
				if (!shouldPrune) {
					shouldPrune = true;
					reason = "unlisted since " + unlistedSince;
				}
			}
		}

		if (shouldDelete) {
			addIfMissing(filesToDelete, metadataFile);
			logger.debug("  - {} ({})", metadataFile.getFileName(), reason);
		} else if (shouldPrune) {
			addIfMissing(filesToPrune, metadataFile);
			for (Path checksumFile : getRelatedChecksumFiles(metadata, metadataFile)) {
				addIfMissing(filesToPrune, checksumFile);
			}
			logger.debug("  - {} ({})", metadataFile.getFileName(), reason);
		}
	}

	private List<Path> getRelatedChecksumFiles(JdkMetadata metadata, Path metadataFile) {
		List<Path> checksumFiles = new ArrayList<>();

		String distro = metadata.getDistro();
		if (distro == null || distro.isBlank()) {
			Path parent = metadataFile.getParent();
			if (parent != null) {
				distro = parent.getFileName().toString();
			}
		}

		if (distro == null || distro.isBlank()) {
			return checksumFiles;
		}

		Path distroChecksumDir = checksumDir.resolve(distro);
		if (!Files.exists(distroChecksumDir) || !Files.isDirectory(distroChecksumDir)) {
			return checksumFiles;
		}

		String metadataFileName = metadataFile.getFileName().toString();
		if (!metadataFileName.endsWith(".json")) {
			return checksumFiles;
		}

		String baseFileName = metadataFileName.substring(0, metadataFileName.length() - 5);
		String[] extensions = {".md5", ".sha1", ".sha256", ".sha512"};

		for (String extension : extensions) {
			Path checksumFile = distroChecksumDir.resolve(baseFileName + extension);
			if (Files.exists(checksumFile) && Files.isRegularFile(checksumFile)) {
				checksumFiles.add(checksumFile);
			}
		}

		return checksumFiles;
	}

	private Path resolvePruneTarget(Path file) {
		Path absoluteFile = file.toAbsolutePath().normalize();
		Path absoluteMetadataDir = metadataDir.toAbsolutePath().normalize();
		Path absoluteChecksumDir = checksumDir.toAbsolutePath().normalize();

		if (absoluteFile.startsWith(absoluteMetadataDir)) {
			Path relative = absoluteMetadataDir.relativize(absoluteFile);
			if (relative.getNameCount() >= 2) {
				return pruneDir.resolve(relative.getName(0).toString())
						.resolve(relative.getFileName().toString());
			}
		}

		if (absoluteFile.startsWith(absoluteChecksumDir)) {
			Path relative = absoluteChecksumDir.relativize(absoluteFile);
			if (relative.getNameCount() >= 2) {
				return pruneDir.resolve(relative.getName(0).toString())
						.resolve(relative.getFileName().toString());
			}
		}

		Path parent = absoluteFile.getParent();
		if (parent != null) {
			Path distro = parent.getFileName();
			if (distro != null) {
				return pruneDir.resolve(distro.toString())
						.resolve(absoluteFile.getFileName().toString());
			}
		}

		return null;
	}

	private void addIfMissing(List<Path> files, Path file) {
		if (!files.contains(file)) {
			files.add(file);
		}
	}

	private boolean matchesUnlistedSince(String unlistedSince, Instant threshold) {
		if (unlistedSince == null || unlistedSince.isBlank()) {
			return false;
		}

		String value = unlistedSince.trim();
		try {
			return !Instant.parse(value).isAfter(threshold);
		} catch (RuntimeException ignored) {
			logger.debug("Ignoring metadata with non-instant unlisted_since value: {}", value);
			return false;
		}
	}

	/**
	 * Remove orphaned checksum files that don't have corresponding metadata files.
	 * This is run after metadata cleanup to remove checksums for deleted metadata.
	 */
	private void removeOrphanedChecksums(Path metadataDistroDir, CleanStats stats, List<Path> filesToDelete) {
		if (!Files.exists(checksumDir) || !Files.isDirectory(checksumDir)) {
			logger.warn("Checksum directory not found: {}", checksumDir.toAbsolutePath());
			return;
		}

		try {
			// Get list of all distro directories from checksums
			List<Path> distroDirs =
					Files.list(checksumDir).filter(Files::isDirectory).toList();

			for (Path distroChecksumDir : distroDirs) {
				String distroName = distroChecksumDir.getFileName().toString();
				Path distroMetadataDir = metadataDistroDir.resolve(distroName);

				// Skip if no metadata directory for this distro
				if (!Files.exists(distroMetadataDir) || !Files.isDirectory(distroMetadataDir)) {
					logger.debug("No metadata directory for distro: {}", distroName);
					continue;
				}

				// List all checksum files for this distro
				List<Path> checksumFiles = Files.list(distroChecksumDir)
						.filter(Files::isRegularFile)
						.filter(p -> {
							String name = p.getFileName().toString();
							return name.endsWith(".md5")
									|| name.endsWith(".sha1")
									|| name.endsWith(".sha256")
									|| name.endsWith(".sha512");
						})
						.toList();

				for (Path checksumFile : checksumFiles) {
					try {
						// Extract base filename by removing checksum extension
						String checksumFileName = checksumFile.getFileName().toString();
						String baseFileName;

						if (checksumFileName.endsWith(".md5")) {
							baseFileName = checksumFileName.substring(0, checksumFileName.length() - 4);
						} else if (checksumFileName.endsWith(".sha1")) {
							baseFileName = checksumFileName.substring(0, checksumFileName.length() - 5);
						} else if (checksumFileName.endsWith(".sha256")) {
							baseFileName = checksumFileName.substring(0, checksumFileName.length() - 7);
						} else if (checksumFileName.endsWith(".sha512")) {
							baseFileName = checksumFileName.substring(0, checksumFileName.length() - 7);
						} else {
							continue; // Skip unrecognized files
						}

						// Check if corresponding metadata file exists
						Path metadataFile = distroMetadataDir.resolve(baseFileName + ".json");

						if (!Files.exists(metadataFile)) {
							// Metadata file doesn't exist, mark checksum for deletion
							stats.orphanedChecksums++;
							addIfMissing(filesToDelete, checksumFile);
							logger.debug(
									"  - {} (orphaned - no metadata file {})",
									checksumFile.getFileName(),
									metadataFile.getFileName());
						}
					} catch (Exception e) {
						logger.error(
								"Failed to check checksum file {}: {}", checksumFile.getFileName(), e.getMessage());
						stats.errors++;
					}
				}
			}
		} catch (IOException e) {
			logger.error("Failed to prune orphaned checksums: {}", e.getMessage());
			stats.errors++;
		}
	}

	/** Statistics for clean operation */
	private static class CleanStats {
		int totalFiles = 0;
		int incompleteChecksums = 0;
		int incompleteReleaseInfo = 0;
		int invalidFiles = 0;
		int oldEaReleases = 0;
		int unlistedReleases = 0;
		int orphanedChecksums = 0;
		int errors = 0;
	}
}
