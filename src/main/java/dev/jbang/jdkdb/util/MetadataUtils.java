package dev.jbang.jdkdb.util;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.util.DefaultIndenter;
import com.fasterxml.jackson.core.util.DefaultPrettyPrinter;
import com.fasterxml.jackson.core.util.Separators;
import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.json.JsonMapper;
import dev.jbang.jdkdb.model.JdkMetadata;
import dev.jbang.jdkdb.scraper.Scraper;
import dev.jbang.jdkdb.scraper.ScraperFactory;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class MetadataUtils {
	private static final Logger logger = LoggerFactory.getLogger(MetadataUtils.class);

	public static final EnumSet<JdkMetadata.FileType> UNPACKABLE_FILE_TYPES = EnumSet.of(
			JdkMetadata.FileType.apk,
			JdkMetadata.FileType.deb,
			JdkMetadata.FileType.msi,
			JdkMetadata.FileType.pkg,
			JdkMetadata.FileType.rpm,
			JdkMetadata.FileType.tar_gz,
			JdkMetadata.FileType.tar_xz,
			JdkMetadata.FileType.zip);

	public static final EnumSet<JdkMetadata.FileType> MACOS_FILE_TYPES =
			EnumSet.of(JdkMetadata.FileType.pkg, JdkMetadata.FileType.dmg);

	private static ObjectMapper readMapper = new ObjectMapper();

	private static final Pattern DURATION_PATTERN = Pattern.compile("^(\\d+)([dwmy])$");
	private static final Pattern VERSION_PATTERN = Pattern.compile("^(?:1\\.)?(\\d+)");

	// Use a mix-in to override the JsonPropertyOrder annotation
	private static ObjectMapper writeMapper = JsonMapper.builder()
			.configure(JsonGenerator.Feature.AUTO_CLOSE_TARGET, false)
			.enable(MapperFeature.SORT_PROPERTIES_ALPHABETICALLY)
			.enable(SerializationFeature.INDENT_OUTPUT)
			.enable(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS)
			.defaultPrettyPrinter(new DefaultPrettyPrinter()
					.withObjectIndenter(new DefaultIndenter("  ", "\n"))
					.withArrayIndenter(new DefaultIndenter("  ", "\n"))
					.withSeparators(
							Separators.createDefaultInstance().withObjectFieldValueSpacing(Separators.Spacing.AFTER)))
			.build();

	// Add a mix-in to override the @JsonPropertyOrder annotation
	static {
		writeMapper.addMixIn(JdkMetadata.class, AlphabeticPropertyOrder.class);
	}

	/** Mix-in to force alphabetical property ordering */
	@JsonPropertyOrder(alphabetic = true)
	private interface AlphabeticPropertyOrder {}

	public static JdkMetadata readMetadataFile(Path metadataFile) throws IOException {
		JdkMetadata md = readMapper.readValue(metadataFile.toFile(), JdkMetadata.class);
		md.metadataFile(metadataFile.toAbsolutePath());
		return md;
	}

	/**
	 * Validate metadata fields and return true if valid, false if invalid.
	 * This checks that required fields are present and that enum values
	 * are valid (or properly marked as unknown).
	 */
	public static boolean isValidMetadata(JdkMetadata metadata) {
		return metadata != null && metadata.isValid();
	}

	public static <T extends Enum<T>> boolean isValidEnum(Class<T> enumClass, String value) {
		return findEnum(enumClass, value).isPresent();
	}

	public static <T extends Enum<T>> boolean isValidEnumOrUnknown(Class<T> enumClass, String value) {
		if (value == null || value.trim().isEmpty()) {
			return false;
		}
		// We will allow "unknown" values, but only if they are properly defined
		// (meaning they will be of the form "unknown-[os|architecture]-<value>")
		// We will NOT allow "unknown" and "unknown-[os|architecture]-" without
		// the value, as that would mean something was not properly normalized
		// and probably the scraper needs to be updated/fixed
		return (value.startsWith("unknown-") && !value.endsWith("-"))
				|| (value.startsWith("unknown_") && !value.endsWith("_"))
				|| findEnum(enumClass, value).isPresent();
	}

	/** Save individual metadata to file */
	public static void saveMetadataFile(Path metadataFile, JdkMetadata metadata) throws IOException {
		try (var writer = Files.newBufferedWriter(metadataFile)) {
			writeMapper.writeValue(writer, metadata);
		}
	}

	/** Save all metadata and create combined all.json file */
	public static void saveMetadata(Path metadataFile, List<JdkMetadata> metadataList) throws IOException {
		// Sort by version first (using VersionComparator) and filename second
		Comparator<JdkMetadata> comparator = Comparator.<JdkMetadata, String>comparing(
						md -> md.getVersion(), VersionComparator.INSTANCE)
				.thenComparing(md -> md.metadataFile().getFileName().toString());

		// Only for debugging purposes
		if (System.getProperty("index.sort", "numerical").equalsIgnoreCase("lexical")) {
			logger.warn(
					"WARNING: FOR DEBUGGING ONLY - Using lexical version sorting for {}", metadataFile.getFileName());
			comparator = Comparator.<JdkMetadata, String>comparing(
					md -> md.metadataFile().getFileName().toString());
		}

		List<JdkMetadata> sortedList = metadataList.stream().sorted(comparator).toList();

		// Copy better data from release_info to top-level fields
		for (JdkMetadata md : sortedList) {
			var ri = md.getReleaseInfo();
			if (ri == null) {
				continue;
			}
			String betterJavaVersion = ri.getOrDefault(
					"JAVA_RUNTIME_VERSION", ri.getOrDefault("FULL_VERSION", ri.getOrDefault("JAVA_VERSION", null)));
			if (betterJavaVersion != null
					&& betterJavaVersion.length() > md.getVersion().length()) {
				// A longer version will be considered "better" (ie containing more details)
				md.setJavaVersion(betterJavaVersion);
			}
		}

		// Clear release_info from all metadata entries before writing lists!
		for (JdkMetadata md : sortedList) {
			md.setReleaseInfo(null);
		}

		// Create all.json
		if (!sortedList.isEmpty()) {
			try (var writer = Files.newBufferedWriter(metadataFile)) {
				writeMapper.writeValue(writer, sortedList);
				// This is to ensure we write the files exactly as the original code did
				writer.write("\n");
			}
		}
	}

	/**
	 * Generate all.json file from all .json files in the source distro directory. Reads individual
	 * metadata files from {@code sourceDistroDir} and writes all.json/latest.json to {@code outputDistroDir}.
	 */
	public static void generateAllJsonFromDirectory(Path sourceDistroDir, Path outputDistroDir, boolean allowIncomplete)
			throws IOException {
		if (!Files.exists(sourceDistroDir) || !Files.isDirectory(sourceDistroDir)) {
			logger.info("No metadata found to generate indices for: {}", sourceDistroDir);
			return;
		}

		List<JdkMetadata> allMetadata = collectAllMetadata(sourceDistroDir, 1, true, allowIncomplete);
		if (allMetadata.isEmpty()) {
			logger.info("No metadata found to generate indices for: {}", sourceDistroDir);
			return;
		}

		Files.createDirectories(outputDistroDir);

		// Save the combined all.json
		logger.info("Generating {}/all.json ({} entries)", outputDistroDir.getFileName(), allMetadata.size());
		saveMetadata(outputDistroDir.resolve("all.json"), allMetadata);

		// Save the combined latest.json
		List<JdkMetadata> filteredList = filterLatestVersions(allMetadata);
		logger.info("Generating {}/latest.json ({} entries)", outputDistroDir.getFileName(), filteredList.size());
		saveMetadata(outputDistroDir.resolve("latest.json"), filteredList);
	}

	/**
	 * Generate comprehensive indices including nested directory structures for release_type,
	 * OS, architecture, image_type, jvm_impl, and distro
	 *
	 * @return The number of index files created
	 */
	public static int generateComprehensiveIndices(Path metadataDir, Path indexDir, boolean allowIncomplete)
			throws IOException {
		logger.info("Generating comprehensive indices...");

		if (!Files.exists(metadataDir) || !Files.isDirectory(metadataDir)) {
			logger.info("No metadata found to generate comprehensive indices.");
			return 0;
		}

		List<JdkMetadata> allMetadata = collectAllMetadata(metadataDir, 2, true, allowIncomplete);
		if (allMetadata.isEmpty()) {
			logger.info("No metadata found to generate comprehensive indices.");
			return 0;
		}

		Files.createDirectories(indexDir);

		int fileCount = 0;

		// Generate metadata/all.json
		// (no longer needed as it is being generated in generateReleaseTypeIndices)
		// logger.info("Generating metadata/all.json ({} entries)", allMetadata.size());
		// saveMetadata(indexDir.resolve("all.json"), allMetadata);

		// Generate latest.json
		logger.debug("Generating latest.json");
		List<JdkMetadata> filteredList = filterLatestVersions(allMetadata);
		saveMetadata(indexDir.resolve("latest.json"), filteredList);
		fileCount++;

		fileCount += generateReleaseTypeIndices(indexDir, indexDir, allMetadata);

		logger.debug("Comprehensive indices generated successfully.");
		return fileCount;
	}

	private static int generateReleaseTypeIndices(Path indexDir, Path baseDir, List<JdkMetadata> metadata)
			throws IOException {
		int fileCount = 0;

		// Group by release_type
		var byReleaseType = metadata.stream()
				.collect(java.util.stream.Collectors.groupingBy(
						md -> normalizeValue(md.getReleaseType(), "unknown-release-type-"),
						java.util.LinkedHashMap::new,
						java.util.stream.Collectors.toList()));

		// We add a special "all" release type that combines GA and EA releases
		byReleaseType.put("all", metadata);

		for (var releaseEntry : byReleaseType.entrySet()) {
			String releaseType = releaseEntry.getKey();

			// We allow the valid names "ga" and "ea" but also the "all" combined type
			if (!isValidEnum(JdkMetadata.ReleaseType.class, releaseType) && !releaseType.equals("all")) {
				logger.warn("Skipping invalid release type: {}", releaseType);
				continue;
			}

			List<JdkMetadata> releaseMetadata = releaseEntry.getValue();
			Path releaseDir = baseDir.resolve(releaseType);
			// First recuirsively delete existing release type directory
			// to ensure we remove old data that is no longer present
			FileUtils.deleteDirectory(releaseDir);
			// Then create a new empty release type directory
			Files.createDirectories(releaseDir);

			// Save release_type-level JSON
			logger.debug("Generating {}.json ({} entries)", releaseType, releaseMetadata.size());
			saveMetadata(baseDir.resolve(releaseType + ".json"), releaseMetadata);
			fileCount++;

			fileCount += generateOsIndices(indexDir, releaseDir, releaseMetadata);
		}

		return fileCount;
	}

	private static int generateOsIndices(Path indexDir, Path baseDir, List<JdkMetadata> metadata) throws IOException {
		int fileCount = 0;

		// Group by OS
		var byOs = metadata.stream()
				.collect(java.util.stream.Collectors.groupingBy(
						md -> normalizeValue(md.getOs(), "unknown-os-"),
						java.util.LinkedHashMap::new,
						java.util.stream.Collectors.toList()));

		for (var osEntry : byOs.entrySet()) {
			String os = osEntry.getKey();

			if (!isValidEnumOrUnknown(JdkMetadata.Os.class, os)) {
				logger.warn("Skipping invalid OS: {}", os);
				continue;
			}

			List<JdkMetadata> osMetadata = osEntry.getValue();
			Path osDir = baseDir.resolve(os);
			Files.createDirectories(osDir);

			// Save OS-level JSON
			Path osJsonFile = baseDir.resolve(os + ".json");
			logger.debug("Generating {} ({} entries)", indexDir.relativize(osJsonFile), osMetadata.size());
			saveMetadata(osJsonFile, osMetadata);
			fileCount++;

			fileCount += generateArchitectureIndices(indexDir, osDir, osMetadata);
		}

		return fileCount;
	}

	private static int generateArchitectureIndices(Path indexDir, Path baseDir, List<JdkMetadata> osMetadata)
			throws IOException {
		int fileCount = 0;

		// Group by architecture
		var byArch = osMetadata.stream()
				.collect(java.util.stream.Collectors.groupingBy(
						md -> normalizeValue(md.getArchitecture(), "unknown-architecture-"),
						java.util.LinkedHashMap::new,
						java.util.stream.Collectors.toList()));

		for (var archEntry : byArch.entrySet()) {
			String arch = archEntry.getKey();

			if (!isValidEnumOrUnknown(JdkMetadata.Arch.class, arch.replace("-", "_"))) {
				logger.warn("Skipping invalid architecture: {}", arch);
				continue;
			}

			List<JdkMetadata> archMetadata = archEntry.getValue();
			Path archDir = baseDir.resolve(arch);
			Files.createDirectories(archDir);

			// Save architecture-level JSON
			Path archJsonFile = baseDir.resolve(arch + ".json");
			logger.debug("Generating {} ({} entries)", indexDir.relativize(archJsonFile), archMetadata.size());
			saveMetadata(archJsonFile, archMetadata);
			fileCount++;

			fileCount += generateImageTypeIndices(indexDir, archDir, archMetadata);
		}

		return fileCount;
	}

	private static int generateImageTypeIndices(Path indexDir, Path baseDir, List<JdkMetadata> archMetadata)
			throws IOException {
		int fileCount = 0;

		// Group by image_type
		var byImageType = archMetadata.stream()
				.collect(java.util.stream.Collectors.groupingBy(
						md -> normalizeValue(md.getImageType(), "unknown-image-type"),
						java.util.LinkedHashMap::new,
						java.util.stream.Collectors.toList()));

		// We add a special "all" image type that combines jdk and jre types
		// TODO: disabled for now because this affects how "latest" works
		// byImageType.put("all", archMetadata);

		for (var imageEntry : byImageType.entrySet()) {
			String imageType = imageEntry.getKey();

			// We allow the valid names "jdk" and "jre" but also the "all" combined type
			if (!isValidEnum(JdkMetadata.ImageType.class, imageType) && !imageType.equals("all")) {
				logger.warn("Skipping invalid image type: {}", imageType);
				continue;
			}

			List<JdkMetadata> imageMetadata = imageEntry.getValue();
			Path imageDir = baseDir.resolve(imageType);
			Files.createDirectories(imageDir);

			// Save image_type-level JSON
			Path imageJsonFile = baseDir.resolve(imageType + ".json");
			logger.debug("Generating {} ({} entries)", indexDir.relativize(imageJsonFile), imageMetadata.size());
			saveMetadata(imageJsonFile, imageMetadata);
			fileCount++;

			fileCount += generateJvmImplIndices(indexDir, imageDir, imageMetadata);
		}

		return fileCount;
	}

	private static int generateJvmImplIndices(Path indexDir, Path baseDir, List<JdkMetadata> imageMetadata)
			throws IOException {
		int fileCount = 0;

		// Group by jvm_impl
		var byJvmImpl = imageMetadata.stream()
				.collect(java.util.stream.Collectors.groupingBy(
						md -> normalizeValue(md.getJvmImpl(), "unknown-jvm-impl"),
						java.util.LinkedHashMap::new,
						java.util.stream.Collectors.toList()));

		for (var jvmEntry : byJvmImpl.entrySet()) {
			String jvmImpl = jvmEntry.getKey();

			if (!isValidEnum(JdkMetadata.JvmImpl.class, jvmImpl)) {
				logger.warn("Skipping invalid JVM implementation: {}", jvmImpl);
				continue;
			}

			List<JdkMetadata> jvmMetadata = jvmEntry.getValue();
			Path jvmDir = baseDir.resolve(jvmImpl);
			Files.createDirectories(jvmDir);

			// Save jvm_impl-level JSON
			Path jvmJsonFile = baseDir.resolve(jvmImpl + ".json");
			logger.debug("Generating {} ({} entries)", indexDir.relativize(jvmJsonFile), jvmMetadata.size());
			saveMetadata(jvmJsonFile, jvmMetadata);
			fileCount++;

			fileCount += generateDistroIndices(indexDir, jvmDir, jvmMetadata);
		}

		return fileCount;
	}

	private static int generateDistroIndices(Path indexDir, Path baseDir, List<JdkMetadata> jvmMetadata)
			throws IOException {
		int fileCount = 0;

		// Group by distro
		var byDistro = jvmMetadata.stream()
				.collect(Collectors.groupingBy(
						md -> normalizeValue(md.getDistro(), "unknown-distro"),
						LinkedHashMap::new,
						Collectors.toList()));

		// We add a special "all" and "latest" distro
		// TODO: determine if we want this or not, disabling for now
		// byDistro.put("all", jvmMetadata);
		// byDistro.put("latest", filterLatestVersions(jvmMetadata));

		Set<String> allDistros = getAllDistros();

		for (var distroEntry : byDistro.entrySet()) {
			String distro = distroEntry.getKey();

			if (!allDistros.contains(distro)
					&& !distro.startsWith("unknown-distro-")
					&& !distro.equals("all")
					&& !distro.equals("latest")) {
				logger.warn("Skipping invalid distro: {}", distro);
				continue;
			}

			List<JdkMetadata> distroMetadata = distroEntry.getValue();
			Path distroDir = baseDir.resolve(distro);
			Files.createDirectories(distroDir);

			// Save distro-level JSON
			Path distroJsonFile = baseDir.resolve(distro + ".json");
			logger.debug("Generating {} ({} entries)", indexDir.relativize(distroJsonFile), distroMetadata.size());
			saveMetadata(distroJsonFile, distroMetadata);
			fileCount++;

			fileCount += generateMajorVersionIndices(indexDir, distroDir, distroMetadata);
		}

		return fileCount;
	}

	private static int generateMajorVersionIndices(Path indexDir, Path baseDir, List<JdkMetadata> distroMetadata)
			throws IOException {
		int fileCount = 0;

		// Group by major Java version
		var byMajorVersion = distroMetadata.stream()
				.collect(java.util.stream.Collectors.groupingBy(
						md -> extractMajorVersion(md.getJavaVersion()),
						java.util.LinkedHashMap::new,
						java.util.stream.Collectors.toList()));

		for (var versionEntry : byMajorVersion.entrySet()) {
			String majorVersion = versionEntry.getKey();

			if (majorVersion.equals("unknown")) {
				logger.warn("Skipping unknown major version");
				continue;
			}

			List<JdkMetadata> versionMetadata = versionEntry.getValue();
			Path versionDir = baseDir.resolve(majorVersion);
			Files.createDirectories(versionDir);

			// Save major version-level JSON
			Path versionJsonFile = baseDir.resolve(majorVersion + ".json");
			logger.debug("Generating {} ({} entries)", indexDir.relativize(versionJsonFile), versionMetadata.size());
			saveMetadata(versionJsonFile, versionMetadata);
			fileCount++;

			fileCount += generateFileTypeIndices(indexDir, versionDir, versionMetadata);
		}

		return fileCount;
	}

	private static int generateFileTypeIndices(Path indexDir, Path baseDir, List<JdkMetadata> versionMetadata)
			throws IOException {
		int fileCount = 0;

		// Group by file type
		var byFileType = versionMetadata.stream()
				.collect(java.util.stream.Collectors.groupingBy(
						md -> normalizeValue(md.getFileType(), "unknown-file-type"),
						java.util.LinkedHashMap::new,
						java.util.stream.Collectors.toList()));

		for (var fileTypeEntry : byFileType.entrySet()) {
			String fileType = fileTypeEntry.getKey();

			if (!isValidEnumOrUnknown(JdkMetadata.FileType.class, fileType.replace(".", "_"))) {
				logger.warn("Skipping invalid file type: {}", fileType);
				continue;
			}

			List<JdkMetadata> fileTypeMetadata = fileTypeEntry.getValue();

			// Save file type-level JSON (leaf level - no directories created)
			Path fileTypeJsonFile = baseDir.resolve(fileType + ".json");
			logger.debug("Generating {} ({} entries)", indexDir.relativize(fileTypeJsonFile), fileTypeMetadata.size());
			saveMetadata(fileTypeJsonFile, fileTypeMetadata);
			fileCount++;

			// Save the same info filtered by latest versions
			List<JdkMetadata> latestFileTypeMetadata = filterLatestVersions(fileTypeMetadata);
			Path latestFileTypeJsonFile = baseDir.resolve(fileType + "-latest.json");
			logger.debug(
					"Generating {} ({} entries)",
					indexDir.relativize(latestFileTypeJsonFile),
					latestFileTypeMetadata.size());
			saveMetadata(latestFileTypeJsonFile, latestFileTypeMetadata);
			fileCount++;
		}

		return fileCount;
	}

	/**
	 * Extract the major version number from a java_version string.
	 * For old versions starting with "1." (e.g., "1.8.0_302"), returns the number after "1." (e.g., "8").
	 * For modern versions (e.g., "17", "11.0.1"), returns the first digits (e.g., "17", "11").
	 *
	 * @param javaVersion The java_version string (e.g., "17", "11.0.1", "8u302", "1.8.0_302")
	 * @return The major version as a string (e.g., "17", "11", "8", "8")
	 */
	private static String extractMajorVersion(String javaVersion) {
		if (javaVersion == null || javaVersion.isEmpty()) {
			return "unknown";
		}
		Matcher matcher = VERSION_PATTERN.matcher(javaVersion);
		return matcher.find() ? matcher.group(1) : "unknown";
	}

	/**
	 * Filter metadata to keep only the latest version per major version for each unique group.
	 * Groups by: os, architecture, image_type, jvm_impl, distro, major_version
	 * Special handling: If both GA and EA exist for the same version, only GA is kept.
	 * All assets for the winning version are included in the result.
	 *
	 * @param metadataList The list of metadata to filter
	 * @return Filtered list containing all assets for the latest version per major version per group
	 */
	private static List<JdkMetadata> filterLatestVersions(List<JdkMetadata> metadataList) {
		// Group by all the required fields including major version
		var grouped = metadataList.stream().collect(Collectors.groupingBy(md -> {
			String os = normalizeValue(md.getOs(), "unknown-os");
			String arch = normalizeValue(md.getArchitecture(), "unknown-arch");
			String imageType = normalizeValue(md.getImageType(), "unknown-image-type");
			String jvmImpl = normalizeValue(md.getJvmImpl(), "unknown-jvm-impl");
			String distro = normalizeValue(md.getDistro(), "unknown-distro");
			String majorVersion = extractMajorVersion(md.getJavaVersion());

			return String.format("%s|%s|%s|%s|%s|%s", os, arch, imageType, jvmImpl, distro, majorVersion);
		}));

		List<JdkMetadata> result = new ArrayList<>();

		// For each group (which represents a unique major version + platform combination)
		for (var group : grouped.values()) {
			// Sort by release_type first (ga before ea), then by version (descending, latest first)
			var sorted = group.stream()
					.sorted(Comparator.<JdkMetadata, String>comparing(
									md -> md.getReleaseType(), Comparator.reverseOrder())
							.thenComparing(md -> md.getVersion(), VersionComparator.INSTANCE.reversed()))
					.toList();

			if (!sorted.isEmpty()) {
				// The first element is the winner (highest version, GA preferred over EA)
				JdkMetadata winner = sorted.get(0);
				String winningVersion = winner.getVersion();
				String winningReleaseType = winner.getReleaseType();

				// Add all assets that match the winning version and release type
				group.stream()
						.filter(md -> md.getVersion().equals(winningVersion)
								&& md.getReleaseType().equals(winningReleaseType))
						.forEach(result::add);
			}
		}

		return result;
	}

	/**
	 * Collect all metadata from the given directory and subdirectories, excluding all.json and latest.json.
	 *
	 * @param dir The directory to search for metadata files
	 * @param maxDepth The maximum depth to search for metadata files
	 * @param includeComplete If true, include metadata entries that have checksum values and release info
	 * @param includeIncomplete If true, include metadata entries that are missing checksum values or release info
	 */
	public static List<JdkMetadata> collectAllMetadata(
			Path dir, int maxDepth, boolean includeComplete, boolean includeIncomplete) throws IOException {
		List<JdkMetadata> allMetadata = new ArrayList<>();
		long[] lastProgressTime = {System.currentTimeMillis()};
		final long PROGRESS_INTERVAL = 10000; // 10 seconds

		logger.info("Collecting metadata...");
		try (Stream<Path> paths = Files.walk(dir, maxDepth)) {
			paths.filter(Files::isRegularFile)
					.filter(p -> p.getFileName().toString().endsWith(".json"))
					.filter(p -> !p.getFileName().toString().equals("all.json"))
					.filter(p -> !p.getFileName().toString().equals("latest.json"))
					.forEach(metadataFile -> {
						try {
							JdkMetadata metadata = readMetadataFile(metadataFile);
							boolean isIncomplete = MetadataUtils.hasMissingChecksums(metadata)
									|| MetadataUtils.hasMissingReleaseInfo(metadata);
							if ((includeComplete && !isIncomplete) || (includeIncomplete && isIncomplete)) {
								allMetadata.add(metadata);
							}

							// Print progress every 10 seconds
							long currentTime = System.currentTimeMillis();
							if (currentTime - lastProgressTime[0] >= PROGRESS_INTERVAL) {
								logger.info("  Scanned {} files...", allMetadata.size());
								lastProgressTime[0] = currentTime;
							}
						} catch (IOException e) {
							logger.error("Failed to read metadata file: {} - {}", metadataFile, e.getMessage());
						}
					});
		}

		logger.info("Collected {} metadata files", allMetadata.size());
		return allMetadata;
	}

	/**
	 * Normalize a value, replacing null or empty strings with a default value
	 */
	private static String normalizeValue(String value, String defaultValue) {
		if (value == null || value.trim().isEmpty()) {
			return defaultValue;
		}
		return value.trim();
	}

	/**
	 * Parse duration string (e.g., "30d", "3w", "6m", "1y") into Duration.
	 *
	 * @param durationStr Duration string with format [number][d|w|m|y]
	 * @return Duration object or null if format is invalid
	 */
	public static Duration parseDuration(String durationStr) {
		Matcher matcher = DURATION_PATTERN.matcher(durationStr.toLowerCase());
		if (!matcher.matches()) {
			return null;
		}

		long amount = Long.parseLong(matcher.group(1));
		String unit = matcher.group(2);

		return switch (unit) {
			case "d" -> Duration.ofDays(amount);
			case "w" -> Duration.ofDays(amount * 7);
			case "m" -> Duration.ofDays(amount * 30); // Approximate month as 30 days
			case "y" -> Duration.ofDays(amount * 365); // Approximate year as 365 days
			default -> null;
		};
	}

	/**
	 * Check if metadata has missing checksums.
	 *
	 * @param metadata The metadata to check
	 * @return true if any of the checksums (md5, sha1, sha256, sha512) are missing
	 */
	public static boolean hasMissingChecksums(JdkMetadata metadata) {
		// Only check files that have a URL (otherwise we can't download them)
		if (metadata.getUrl() == null || metadata.getFilename() == null) {
			return false;
		}

		// Check if any of the primary checksums are missing
		return metadata.getMd5() == null
				|| metadata.getSha1() == null
				|| metadata.getSha256() == null
				|| metadata.getSha512() == null;
	}

	/**
	 * Check if metadata has missing checksums.
	 *
	 * @param metadata The metadata to check
	 * @return true if the release info is missing
	 */
	public static boolean hasMissingReleaseInfo(JdkMetadata metadata) {
		// Only check files that have a URL (otherwise we can't download them)
		if (metadata.getUrl() == null || metadata.getFilename() == null) {
			return false;
		}

		if (!isValidEnum(JdkMetadata.FileType.class, metadata.getFileType().replace(".", "_"))) {
			// For unknown file types we can't reliably determine if the release
			// info is missing or not, so we will not consider it incomplete
			return false;
		}

		if (!UNPACKABLE_FILE_TYPES.contains(metadata.fileTypeEnum())) {
			// For non-unpackable file types we can't extract release info,
			// so we will not consider it incomplete
			return false;
		}

		// Check if any of the primary release info fields is missing
		return metadata.getReleaseInfo() == null;
	}

	public static <T extends Enum<T>> Optional<T> findEnum(Class<T> enumClass, String value) {
		try {
			return Optional.of(Enum.valueOf(enumClass, value));
		} catch (IllegalArgumentException | NullPointerException e) {
			return Optional.empty();
		}
	}

	private static class DistroVendorCache {
		private static final Map<String, String> DISTRO_VENDOR_MAP = buildDistroVendorMap();
		private static final Set<String> VENDORS = new HashSet<>(DISTRO_VENDOR_MAP.values());

		private static Map<String, String> buildDistroVendorMap() {
			Map<String, String> map = new HashMap<>();
			Collection<Scraper.Discovery> discs =
					ScraperFactory.getAvailableScraperDiscoveries().values();
			for (Scraper.Discovery discovery : discs) {
				map.put(discovery.distro(), discovery.vendor());
			}
			return map;
		}
	}

	public static Map<String, String> getDistroVendorMapping() {
		return DistroVendorCache.DISTRO_VENDOR_MAP;
	}

	public static Set<String> getAllDistros() {
		Map<String, String> map = getDistroVendorMapping();
		return map.keySet();
	}

	public static Set<String> getAllVendors() {
		return DistroVendorCache.VENDORS;
	}

	public static boolean isValidReleaseInfo(Map<String, String> releaseInfo) {
		if (releaseInfo == null) {
			// Missing release info is invalid
			return false;
		}
		if (releaseInfo.isEmpty()) {
			// Empty release info is okay
			return true;
		}
		// For now we will just accept any release info that is not null,
		// but we could add more validation here if needed
		return true;
	}
}
