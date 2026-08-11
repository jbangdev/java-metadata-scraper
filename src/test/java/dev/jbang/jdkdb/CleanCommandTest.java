package dev.jbang.jdkdb;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import picocli.CommandLine;

class CleanCommandTest {

	@TempDir
	Path tempDir;

	@Test
	void pruneUnlistedWithoutSinceReturnsError() throws Exception {
		Path metadataRoot = tempDir.resolve("metadata");
		Path checksumRoot = tempDir.resolve("checksums");
		Path pruneRoot = tempDir.resolve("pruned");

		Path distroMetadata = metadataRoot.resolve("temurin");
		Path distroChecksums = checksumRoot.resolve("temurin");
		Files.createDirectories(distroMetadata);
		Files.createDirectories(distroChecksums);

		Path unlisted = distroMetadata.resolve("unlisted.json");
		Path listed = distroMetadata.resolve("listed.json");
		Files.writeString(
				unlisted,
				metadataJson(
						"temurin",
						"unlisted",
						Instant.now()
								.minus(1, ChronoUnit.DAYS)
								.truncatedTo(ChronoUnit.SECONDS)
								.toString()));
		Files.writeString(listed, metadataJson("temurin", "listed", null));
		Files.writeString(distroChecksums.resolve("unlisted.sha256"), "abc");

		int exit = new CommandLine(new CleanCommand())
				.execute(
						"--metadata-dir",
						metadataRoot.toString(),
						"--checksum-dir",
						checksumRoot.toString(),
						"--prune-dir",
						pruneRoot.toString(),
						"--prune-unlisted");

		assertThat(exit).isEqualTo(1);
		assertThat(unlisted).exists();
		assertThat(pruneRoot.resolve("temurin/unlisted.json")).doesNotExist();
		assertThat(distroChecksums.resolve("unlisted.sha256")).exists();
		assertThat(pruneRoot.resolve("temurin/unlisted.sha256")).doesNotExist();
		assertThat(listed).exists();
	}

	@Test
	void pruneUnlistedWithDurationOnlyPrunesOlderMarkers() throws Exception {
		Path metadataRoot = tempDir.resolve("metadata");
		Path checksumRoot = tempDir.resolve("checksums");
		Path pruneRoot = tempDir.resolve("pruned");
		Path distroMetadata = metadataRoot.resolve("temurin");
		Files.createDirectories(distroMetadata);
		Files.createDirectories(checksumRoot.resolve("temurin"));

		String oldDate = Instant.now()
				.minus(10, ChronoUnit.DAYS)
				.truncatedTo(ChronoUnit.SECONDS)
				.toString();
		String recentDate = Instant.now()
				.minus(2, ChronoUnit.DAYS)
				.truncatedTo(ChronoUnit.SECONDS)
				.toString();

		Path oldUnlisted = distroMetadata.resolve("old-unlisted.json");
		Path recentUnlisted = distroMetadata.resolve("recent-unlisted.json");
		Files.writeString(oldUnlisted, metadataJson("temurin", "old-unlisted", oldDate));
		Files.writeString(recentUnlisted, metadataJson("temurin", "recent-unlisted", recentDate));

		int exit = new CommandLine(new CleanCommand())
				.execute(
						"--metadata-dir",
						metadataRoot.toString(),
						"--checksum-dir",
						checksumRoot.toString(),
						"--prune-dir",
						pruneRoot.toString(),
						"--prune-unlisted",
						"5d");

		assertThat(exit).isZero();
		assertThat(oldUnlisted).doesNotExist();
		assertThat(pruneRoot.resolve("temurin/old-unlisted.json")).exists();
		assertThat(recentUnlisted).exists();
	}

	@Test
	void pruneUnlistedWithInvalidSinceReturnsError() {
		int exit = new CommandLine(new CleanCommand()).execute("--prune-unlisted", "not-a-date");

		assertThat(exit).isEqualTo(1);
	}

	@Test
	void pruneInvalidMovesMetadataToPruneDir() throws Exception {
		Path metadataRoot = tempDir.resolve("metadata");
		Path checksumRoot = tempDir.resolve("checksums");
		Path pruneRoot = tempDir.resolve("pruned");
		Path distroMetadata = metadataRoot.resolve("temurin");
		Files.createDirectories(distroMetadata);
		Files.createDirectories(checksumRoot.resolve("temurin"));

		Path invalidMetadata = distroMetadata.resolve("invalid.json");
		Files.writeString(invalidMetadata, "{}");

		int exit = new CommandLine(new CleanCommand())
				.execute(
						"--metadata-dir",
						metadataRoot.toString(),
						"--checksum-dir",
						checksumRoot.toString(),
						"--prune-dir",
						pruneRoot.toString(),
						"--prune-invalid");

		assertThat(exit).isZero();
		assertThat(invalidMetadata).doesNotExist();
		assertThat(pruneRoot.resolve("temurin/invalid.json")).exists();
	}

	private String metadataJson(String distro, String filename, String unlistedSince) {
		String unlisted = unlistedSince == null ? "" : "\n  \"unlisted_since\": \"" + unlistedSince + "\",";
		return """
				{
				\"distro\": \"%s\",
				\"filename\": \"%s\",%s
				\"version\": \"21.0.1\",
				\"java_version\": \"21\",
				\"release_type\": \"ga\",
				\"jvm_impl\": \"hotspot\",
				\"os\": \"linux\",
				\"architecture\": \"x86_64\",
				\"file_type\": \"tar.gz\",
				\"image_type\": \"jdk\",
				\"url\": \"https://example.com/%s.tar.gz\"
				}
				""".formatted(distro, filename, unlisted, filename);
	}
}
