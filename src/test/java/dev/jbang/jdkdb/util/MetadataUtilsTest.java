package dev.jbang.jdkdb.util;

import static org.assertj.core.api.Assertions.*;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.jbang.jdkdb.model.JdkMetadata;
import dev.jbang.jdkdb.scraper.DownloadResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class MetadataUtilsTest {

	@TempDir
	Path tempDir;

	@Test
	void testValidSaveMetadata() throws Exception {
		// Given
		String json = """
			{
				"distro" : "microsoft",
				"vendor" : "microsoft",
				"filename" : "microsoft-jdk-25.0.2-macos-x64.tar.gz",
				"release_type" : "ga",
				"version" : "25.0.2",
				"java_version" : "25.0.2",
				"jvm_impl" : "hotspot",
				"os" : "macosx",
				"architecture" : "x86_64",
				"file_type" : "tar.gz",
				"image_type" : "jdk",
				"features" : [ ],
				"url" : "https://aka.ms/download-jdk/microsoft-jdk-25.0.2-macos-x64.tar.gz",
				"checksum" : "741a6df853edecfadd81b4a389271eb937c2626a",
				"checksum_type" : "sha1",
				"md5" : "37d1c8e9537cd4d75fd28147a5dd6a55",
				"sha1" : "741a6df853edecfadd81b4a389271eb937c2626a",
				"sha256" : "6bc02fd3182dee12510f253d08eeac342a1f0e03d7f4114763f83d8722e2915e",
				"sha512" : "4615da0674699a41cc62ed6ddf201efe1904091cdbf95d629178c2026b312f7bec2fb424ef0c10d2270b086b72b0ca006cdbbcbe1e7df4f54412b8ebb8c37ab5",
				"size" : 220223851
				}""";
		// spotless:off
		String expected = """
[
  {
    "architecture": "x86_64",
    "checksum": "741a6df853edecfadd81b4a389271eb937c2626a",
    "checksum_type": "sha1",
    "distro": "microsoft",
    "features": [ ],
    "file_type": "tar.gz",
    "filename": "microsoft-jdk-25.0.2-macos-x64.tar.gz",
    "image_type": "jdk",
    "java_version": "25.0.2",
    "jvm_impl": "hotspot",
    "md5": "37d1c8e9537cd4d75fd28147a5dd6a55",
    "os": "macosx",
    "release_type": "ga",
    "sha1": "741a6df853edecfadd81b4a389271eb937c2626a",
    "sha256": "6bc02fd3182dee12510f253d08eeac342a1f0e03d7f4114763f83d8722e2915e",
    "sha512": "4615da0674699a41cc62ed6ddf201efe1904091cdbf95d629178c2026b312f7bec2fb424ef0c10d2270b086b72b0ca006cdbbcbe1e7df4f54412b8ebb8c37ab5",
    "size": 220223851,
    "url": "https://aka.ms/download-jdk/microsoft-jdk-25.0.2-macos-x64.tar.gz",
    "vendor": "microsoft",
    "version": "25.0.2"
  }
]
""";
		// spotless:on

		JdkMetadata metadata = parseMetadata(json);
		Files.createDirectories(tempDir.resolve("metadata"));

		// When
		Path metadataFile = tempDir.resolve("metadata").resolve("all.json");
		MetadataUtils.saveMetadata(metadataFile, List.of(metadata));

		// Then
		assertThat(metadataFile).exists();
		String fileContent = Files.readString(metadataFile);
		assertThat(fileContent).isEqualTo(expected);
	}

	@Test
	void testGenerateAllJsonFromDirectory() throws Exception {
		// Given - create individual metadata files
		Path distroDir = tempDir.resolve("distro-dir");
		Files.createDirectories(distroDir);

		DownloadResult download1 = new DownloadResult("md5-1", "sha1-1", "sha256-1", "sha512-1", 100_000_000L);
		JdkMetadata metadata1 = JdkMetadata.create()
				.setDistro("test-distro")
				.setFilename("test-jdk-1")
				.setReleaseType("ga")
				.setVersion("17.0.1")
				.setJavaVersion("17")
				.setOs("linux")
				.setArchitecture("x86_64")
				.setFileType("tar.gz")
				.setImageType("jdk")
				.setUrl("https://example.com/jdk-1.tar.gz")
				.download(download1);

		DownloadResult download2 = new DownloadResult("md5-2", "sha1-2", "sha256-2", "sha512-2", 100_000_001L);
		JdkMetadata metadata2 = JdkMetadata.create()
				.setDistro("test-distro")
				.setFilename("test-jdk-2")
				.setReleaseType("ga")
				.setVersion("17.0.2")
				.setJavaVersion("17")
				.setOs("windows")
				.setArchitecture("x86_64")
				.setFileType("zip")
				.setImageType("jdk")
				.setUrl("https://example.com/jdk-2.zip")
				.download(download2)
				.metadataFile(Path.of("custom-metadata-filename.json"));

		// Save individual metadata files
		MetadataUtils.saveMetadataFile(distroDir.resolve(metadata1.metadataFile()), metadata1);
		MetadataUtils.saveMetadataFile(distroDir.resolve(metadata2.metadataFile()), metadata2);

		// Verify individual files exist
		assertThat(distroDir.resolve("test-jdk-1.json")).exists();
		assertThat(distroDir.resolve("test-jdk-2.json")).doesNotExist();
		assertThat(distroDir.resolve("custom-metadata-filename.json")).exists();

		// When - generate all.json from directory
		MetadataUtils.generateAllJsonFromDirectory(distroDir, distroDir, true);

		// Then - all.json should be created
		Path allJson = distroDir.resolve("all.json");
		assertThat(allJson).exists();

		String allJsonContent = Files.readString(allJson);
		assertThat(allJsonContent).contains("test-jdk-1");
		assertThat(allJsonContent).contains("test-jdk-2");
		assertThat(allJsonContent).contains("17.0.1");
		assertThat(allJsonContent).contains("17.0.2");
	}

	@Test
	void testGenerateAllJsonFromDirectoryIgnoresExistingAllJson() throws Exception {
		// Given - create metadata files including an existing all.json
		Path distroDir = tempDir.resolve("distro-dir");
		Files.createDirectories(distroDir);

		DownloadResult download = new DownloadResult(null, null, null, null, 100_000_000L);
		JdkMetadata metadata = JdkMetadata.create()
				.setFilename("test-jdk")
				.setDistro("test-distro")
				.setVersion("17.0.1")
				.setJavaVersion("17")
				.setOs("linux")
				.setArchitecture("x86_64")
				.setFileType("tar.gz")
				.setImageType("jdk")
				.setReleaseType("ga")
				.setUrl("https://example.com/jdk.tar.gz")
				.download(download);

		MetadataUtils.saveMetadataFile(distroDir.resolve(metadata.metadataFile()), metadata);

		// Create an old all.json with different content
		Files.writeString(distroDir.resolve("all.json"), "[{\"old\": \"data\"}]");

		// When - regenerate all.json
		MetadataUtils.generateAllJsonFromDirectory(distroDir, distroDir, true);

		// Then - new all.json should contain current metadata, not old data
		String allJsonContent = Files.readString(distroDir.resolve("all.json"));
		assertThat(allJsonContent).contains("test-jdk");
		assertThat(allJsonContent).doesNotContain("old");
	}

	@Test
	void testGenerateAllJsonFromNonExistentDirectory() throws Exception {
		// Given - non-existent directory
		Path nonExistent = tempDir.resolve("does-not-exist");

		// When/Then - should not throw exception
		assertThatCode(() -> MetadataUtils.generateAllJsonFromDirectory(nonExistent, nonExistent, true))
				.doesNotThrowAnyException();
	}

	@Test
	void testSaveMetadataSortsByVersionThenFilename() throws Exception {
		// Given - metadata with various versions and filenames
		DownloadResult download1 = new DownloadResult(null, null, null, null, 100_000_000L);
		JdkMetadata metadata1 = JdkMetadata.create()
				.setFilename("zulu-jdk-11.0.10")
				.setDistro("zulu")
				.setVersion("11.0.10")
				.setJavaVersion("11")
				.setOs("linux")
				.setArchitecture("x86_64")
				.setFileType("tar.gz")
				.setImageType("jdk")
				.setReleaseType("ga")
				.setUrl("https://example.com/zulu-11.0.10.tar.gz")
				.download(download1);

		DownloadResult download2 = new DownloadResult(null, null, null, null, 100_000_000L);
		JdkMetadata metadata2 = JdkMetadata.create()
				.setFilename("abc-jdk-11.0.10")
				.setDistro("abc")
				.setVersion("11.0.10")
				.setJavaVersion("11")
				.setOs("linux")
				.setArchitecture("x86_64")
				.setFileType("tar.gz")
				.setImageType("jdk")
				.setReleaseType("ga")
				.setUrl("https://example.com/abc-11.0.10.tar.gz")
				.download(download2);

		DownloadResult download3 = new DownloadResult(null, null, null, null, 100_000_000L);
		JdkMetadata metadata3 = JdkMetadata.create()
				.setFilename("temurin-jdk-17.0.5")
				.setDistro("temurin")
				.setVersion("17.0.5")
				.setJavaVersion("17")
				.setOs("linux")
				.setArchitecture("x86_64")
				.setFileType("tar.gz")
				.setImageType("jdk")
				.setReleaseType("ga")
				.setUrl("https://example.com/temurin-17.0.5.tar.gz")
				.download(download3);

		DownloadResult download4 = new DownloadResult(null, null, null, null, 100_000_000L);
		JdkMetadata metadata4 = JdkMetadata.create()
				.setFilename("oracle-jdk-8.0.202")
				.setDistro("oracle")
				.setVersion("8.0.202")
				.setJavaVersion("8")
				.setOs("linux")
				.setArchitecture("x86_64")
				.setFileType("tar.gz")
				.setImageType("jdk")
				.setReleaseType("ga")
				.setUrl("https://example.com/oracle-8.0.202.tar.gz")
				.download(download4);

		DownloadResult download5 = new DownloadResult(null, null, null, null, 100_000_000L);
		JdkMetadata metadata5 = JdkMetadata.create()
				.setFilename("oracle-jdk-11.0.2")
				.setDistro("oracle")
				.setVersion("11.0.2")
				.setJavaVersion("11")
				.setOs("linux")
				.setArchitecture("x86_64")
				.setFileType("tar.gz")
				.setImageType("jdk")
				.setReleaseType("ga")
				.setUrl("https://example.com/oracle-11.0.2.tar.gz")
				.download(download5);

		// When - save in random order
		Path metadataDir = tempDir.resolve("metadata");
		Files.createDirectories(metadataDir);
		Path allJson = metadataDir.resolve("all.json");
		MetadataUtils.saveMetadata(allJson, List.of(metadata3, metadata1, metadata5, metadata4, metadata2));

		// Then - all.json should be sorted by version first, then filename
		assertThat(allJson).exists();

		String content = Files.readString(allJson);
		JdkMetadata[] result = parseMetadatas(content);

		// Verify sorted order: 8.0.202, 11.0.2, 11.0.10 (abc before zulu), 17.0.5
		assertThat(result).hasSize(5);
		assertThat(result[0].getVersion()).isEqualTo("8.0.202");
		assertThat(result[0].getFilename()).isEqualTo("oracle-jdk-8.0.202");

		assertThat(result[1].getVersion()).isEqualTo("11.0.2");
		assertThat(result[1].getFilename()).isEqualTo("oracle-jdk-11.0.2");

		assertThat(result[2].getVersion()).isEqualTo("11.0.10");
		assertThat(result[2].getFilename()).isEqualTo("abc-jdk-11.0.10"); // abc comes before zulu

		assertThat(result[3].getVersion()).isEqualTo("11.0.10");
		assertThat(result[3].getFilename()).isEqualTo("zulu-jdk-11.0.10");

		assertThat(result[4].getVersion()).isEqualTo("17.0.5");
		assertThat(result[4].getFilename()).isEqualTo("temurin-jdk-17.0.5");
	}

	@Test
	void testGenerateComprehensiveIndicesBasic() throws Exception {
		// Given - create distro directories with metadata files
		Path metadataDir = tempDir.resolve("metadata");
		Path distroDir = metadataDir;
		Path temurinDir = distroDir.resolve("temurin");
		Files.createDirectories(temurinDir);

		DownloadResult download1 = new DownloadResult("md5-1", "sha1-1", "sha256-1", "sha512-1", 100_000_000L);
		JdkMetadata metadata1 = JdkMetadata.create()
				.setDistro("temurin")
				.setFilename("temurin-jdk-17-linux-x64.tar.gz")
				.setReleaseType("ga")
				.setVersion("17.0.5")
				.setJavaVersion("17")
				.setJvmImpl("hotspot")
				.setOs("linux")
				.setArchitecture("x86_64")
				.setFileType("tar.gz")
				.setImageType("jdk")
				.setUrl("https://example.com/temurin-17.tar.gz")
				.setReleaseInfo(Collections.emptyMap())
				.download(download1);

		DownloadResult download2 = new DownloadResult("md5-2", "sha1-2", "sha256-2", "sha512-2", 100_000_001L);
		JdkMetadata metadata2 = JdkMetadata.create()
				.setDistro("temurin")
				.setFilename("temurin-jdk-17-windows-x64.zip")
				.setReleaseType("ga")
				.setVersion("17.0.5")
				.setJavaVersion("17")
				.setJvmImpl("hotspot")
				.setOs("windows")
				.setArchitecture("x86_64")
				.setFileType("zip")
				.setImageType("jdk")
				.setUrl("https://example.com/temurin-17.zip")
				.setReleaseInfo(Collections.emptyMap())
				.download(download2);

		MetadataUtils.saveMetadataFile(temurinDir.resolve(metadata1.metadataFile()), metadata1);
		MetadataUtils.saveMetadataFile(temurinDir.resolve(metadata2.metadataFile()), metadata2);

		// When
		MetadataUtils.generateComprehensiveIndices(metadataDir, metadataDir, false);

		// Then - verify metadata/all.json exists
		Path allJson = metadataDir.resolve("all.json");
		assertThat(allJson).exists();
		String allJsonContent = Files.readString(allJson);
		assertThat(allJsonContent).contains("temurin-jdk-17-linux-x64.tar.gz");
		assertThat(allJsonContent).contains("temurin-jdk-17-windows-x64.zip");

		// Verify hierarchical structure: ga.json
		Path gaDir = metadataDir.resolve("ga");
		assertThat(gaDir).exists().isDirectory();
		Path gaJson = metadataDir.resolve("ga.json");
		assertThat(gaJson).exists();

		// Verify OS level: ga/linux/, ga/linux.json and ga/windows/, ga/windows.json
		Path linuxDir = gaDir.resolve("linux");
		assertThat(linuxDir).exists().isDirectory();
		assertThat(gaDir.resolve("linux.json")).exists();

		Path windowsDir = gaDir.resolve("windows");
		assertThat(windowsDir).exists().isDirectory();
		assertThat(gaDir.resolve("windows.json")).exists();

		// Verify architecture level: ga/linux/x86_64/x86_64.json
		Path archLinuxDir = linuxDir.resolve("x86_64");
		assertThat(archLinuxDir).exists().isDirectory();
		assertThat(linuxDir.resolve("x86_64.json")).exists();

		// Verify image_type level: ga/linux/x86_64/jdk/jdk.json
		Path imageTypeDir = archLinuxDir.resolve("jdk");
		assertThat(imageTypeDir).exists().isDirectory();
		assertThat(archLinuxDir.resolve("jdk.json")).exists();

		// Verify jvm_impl level: ga/linux/x86_64/jdk/hotspot.json
		Path jvmDir = imageTypeDir.resolve("hotspot");
		assertThat(jvmDir).exists().isDirectory();
		assertThat(imageTypeDir.resolve("hotspot.json")).exists();

		// Verify distro level: ga/linux/x86_64/jdk/hotspot/temurin.json
		Path distroJson = jvmDir.resolve("temurin.json");
		assertThat(distroJson).exists();
		String distroContent = Files.readString(distroJson);
		assertThat(distroContent).contains("temurin-jdk-17-linux-x64.tar.gz");
		assertThat(distroContent).doesNotContain("windows"); // Should only contain Linux entry
	}

	@Test
	void testGenerateComprehensiveIndicesMultipleDistros() throws Exception {
		// Given - multiple distros with overlapping properties
		Path metadataDir = tempDir.resolve("metadata");
		Path distroDir = metadataDir;
		Path temurinDir = distroDir.resolve("temurin");
		Path microsoftDir = distroDir.resolve("microsoft");
		Files.createDirectories(temurinDir);
		Files.createDirectories(microsoftDir);

		DownloadResult download1 = new DownloadResult("md5-1", "sha1-1", "sha256-1", "sha512-1", 100_000_000L);
		JdkMetadata metadata1 = JdkMetadata.create()
				.setDistro("temurin")
				.setFilename("temurin-jdk-17.tar.gz")
				.setReleaseType("ga")
				.setVersion("17.0.5")
				.setJavaVersion("17")
				.setJvmImpl("hotspot")
				.setOs("linux")
				.setArchitecture("x86_64")
				.setFileType("tar.gz")
				.setImageType("jdk")
				.setUrl("https://example.com/temurin.tar.gz")
				.setReleaseInfo(Collections.emptyMap())
				.download(download1);

		DownloadResult download2 = new DownloadResult("md5-2", "sha1-2", "sha256-2", "sha512-2", 100_000_001L);
		JdkMetadata metadata2 = JdkMetadata.create()
				.setDistro("microsoft")
				.setFilename("microsoft-jdk-17.tar.gz")
				.setReleaseType("ga")
				.setVersion("17.0.5")
				.setJavaVersion("17")
				.setJvmImpl("hotspot")
				.setOs("linux")
				.setArchitecture("x86_64")
				.setFileType("tar.gz")
				.setImageType("jdk")
				.setUrl("https://example.com/microsoft.tar.gz")
				.setReleaseInfo(Collections.emptyMap())
				.download(download2);

		MetadataUtils.saveMetadataFile(temurinDir.resolve(metadata1.metadataFile()), metadata1);
		MetadataUtils.saveMetadataFile(microsoftDir.resolve(metadata2.metadataFile()), metadata2);

		// When
		MetadataUtils.generateComprehensiveIndices(metadataDir, metadataDir, false);

		// Then - verify both distros appear in the same hierarchical path
		Path distroPath = metadataDir.resolve("ga/linux/x86_64/jdk/hotspot");
		Path temurinJson = distroPath.resolve("temurin.json");
		Path microsoftJson = distroPath.resolve("microsoft.json");

		assertThat(temurinJson).exists();
		assertThat(microsoftJson).exists();

		String temurinContent = Files.readString(temurinJson);
		assertThat(temurinContent).contains("temurin-jdk-17.tar.gz");
		assertThat(temurinContent).doesNotContain("microsoft");

		String microsoftContent = Files.readString(microsoftJson);
		assertThat(microsoftContent).contains("microsoft-jdk-17.tar.gz");
		assertThat(microsoftContent).doesNotContain("temurin");
	}

	@Test
	void testGenerateComprehensiveIndicesWithDifferentReleaseTypes() throws Exception {
		// Given - metadata with different release types
		Path metadataDir = tempDir.resolve("metadata");
		Path distroDir = metadataDir;
		Path temurinDir = distroDir.resolve("temurin");
		Files.createDirectories(temurinDir);

		DownloadResult download1 = new DownloadResult("md5-1", "sha1-1", "sha256-1", "sha512-1", 100_000_000L);
		JdkMetadata metadata1 = JdkMetadata.create()
				.setDistro("temurin")
				.setFilename("temurin-jdk-17-ga.tar.gz")
				.setReleaseType("ga")
				.setVersion("17.0.5")
				.setJavaVersion("17")
				.setJvmImpl("hotspot")
				.setOs("linux")
				.setArchitecture("x86_64")
				.setFileType("tar.gz")
				.setImageType("jdk")
				.setUrl("https://example.com/ga.tar.gz")
				.setReleaseInfo(Collections.emptyMap())
				.download(download1);

		DownloadResult download2 = new DownloadResult("md5-2", "sha1-2", "sha256-2", "sha512-2", 100_000_001L);
		JdkMetadata metadata2 = JdkMetadata.create()
				.setDistro("temurin")
				.setFilename("temurin-jdk-18-ea.tar.gz")
				.setReleaseType("ea")
				.setVersion("18-ea")
				.setJavaVersion("18")
				.setJvmImpl("hotspot")
				.setOs("linux")
				.setArchitecture("x86_64")
				.setFileType("tar.gz")
				.setImageType("jdk")
				.setUrl("https://example.com/ea.tar.gz")
				.setReleaseInfo(Collections.emptyMap())
				.download(download2);

		MetadataUtils.saveMetadataFile(temurinDir.resolve(metadata1.metadataFile()), metadata1);
		MetadataUtils.saveMetadataFile(temurinDir.resolve(metadata2.metadataFile()), metadata2);

		// When
		MetadataUtils.generateComprehensiveIndices(metadataDir, metadataDir, false);

		// Then - verify separate release_type directories
		Path gaDir = metadataDir.resolve("ga");
		Path eaDir = metadataDir.resolve("ea");

		assertThat(gaDir).exists().isDirectory();
		assertThat(eaDir).exists().isDirectory();

		assertThat(metadataDir.resolve("ga.json")).exists();
		assertThat(metadataDir.resolve("ea.json")).exists();

		// Verify content is separated correctly
		String gaContent = Files.readString(metadataDir.resolve("ga.json"));
		assertThat(gaContent).contains("temurin-jdk-17-ga.tar.gz");
		assertThat(gaContent).doesNotContain("temurin-jdk-18-ea.tar.gz");

		String eaContent = Files.readString(metadataDir.resolve("ea.json"));
		assertThat(eaContent).contains("temurin-jdk-18-ea.tar.gz");
		assertThat(eaContent).doesNotContain("temurin-jdk-17-ga.tar.gz");
	}

	@Test
	void testGenerateComprehensiveIndicesEmptyDistroDir() throws Exception {
		// Given - empty distro directory
		Path metadataDir = tempDir.resolve("metadata");
		Path distroDir = metadataDir;
		Files.createDirectories(distroDir);

		// When/Then - should not throw exception
		assertThatCode(() -> MetadataUtils.generateComprehensiveIndices(metadataDir, metadataDir, false))
				.doesNotThrowAnyException();

		// all.json should not be created
		assertThat(metadataDir.resolve("all.json")).doesNotExist();
	}

	@Test
	void testGenerateComprehensiveIndicesNonExistentDistroDir() throws Exception {
		// Given - non-existent metadata directory
		Path metadataDir = tempDir.resolve("does-not-exist");

		// When/Then - should not throw exception
		assertThatCode(() -> MetadataUtils.generateComprehensiveIndices(metadataDir, metadataDir, false))
				.doesNotThrowAnyException();
	}

	@Test
	void testGenerateComprehensiveIndicesFiltersIncompleteMetadata() throws Exception {
		// Given - metadata with and without checksums
		Path metadataDir = tempDir.resolve("metadata");
		Path distroDir = metadataDir;
		Path temurinDir = distroDir.resolve("temurin");
		Files.createDirectories(temurinDir);

		// Complete metadata
		DownloadResult download1 = new DownloadResult("md5-1", "sha1-1", "sha256-1", "sha512-1", 100_000_000L);
		JdkMetadata metadata1 = JdkMetadata.create()
				.setDistro("temurin")
				.setFilename("complete-jdk.tar.gz")
				.setReleaseType("ga")
				.setVersion("17.0.5")
				.setJavaVersion("17")
				.setJvmImpl("hotspot")
				.setOs("linux")
				.setArchitecture("x86_64")
				.setFileType("tar.gz")
				.setImageType("jdk")
				.setUrl("https://example.com/complete.tar.gz")
				.setReleaseInfo(Collections.emptyMap())
				.download(download1);

		// Incomplete metadata (missing checksums)
		DownloadResult download2 = new DownloadResult(null, null, null, null, 100_000_001L);
		JdkMetadata metadata2 = JdkMetadata.create()
				.setDistro("temurin")
				.setFilename("incomplete-jdk.tar.gz")
				.setReleaseType("ga")
				.setVersion("17.0.5")
				.setJavaVersion("17")
				.setJvmImpl("hotspot")
				.setOs("linux")
				.setArchitecture("x86_64")
				.setFileType("tar.gz")
				.setImageType("jdk")
				.setUrl("https://example.com/incomplete.tar.gz")
				.setReleaseInfo(Collections.emptyMap())
				.download(download2);

		MetadataUtils.saveMetadataFile(temurinDir.resolve(metadata1.metadataFile()), metadata1);
		MetadataUtils.saveMetadataFile(temurinDir.resolve(metadata2.metadataFile()), metadata2);

		// When - generate with allowIncomplete=false
		MetadataUtils.generateComprehensiveIndices(metadataDir, metadataDir, false);

		// Then - incomplete metadata should be filtered out
		Path allJson = metadataDir.resolve("all.json");
		assertThat(allJson).exists();
		String allContent = Files.readString(allJson);
		assertThat(allContent).contains("complete-jdk.tar.gz");
		assertThat(allContent).doesNotContain("incomplete-jdk.tar.gz");

		// When - generate with allowIncomplete=true
		MetadataUtils.generateComprehensiveIndices(metadataDir, metadataDir, true);

		// Then - both should be included
		allContent = Files.readString(allJson);
		assertThat(allContent).contains("complete-jdk.tar.gz");
		assertThat(allContent).contains("incomplete-jdk.tar.gz");
	}

	@Test
	void testGenerateComprehensiveIndicesNormalizesEmptyValues() throws Exception {
		// Given - metadata with null/empty values
		Path metadataDir = tempDir.resolve("metadata");
		Path distroDir = metadataDir;
		Path testDir = distroDir.resolve("test");
		Files.createDirectories(testDir);

		DownloadResult download = new DownloadResult("md5-1", "sha1-1", "sha256-1", "sha512-1", 100_000_000L);
		JdkMetadata metadata = JdkMetadata.create()
				.setDistro("unknown-distro-mydistro") // null distro
				.setFilename("test-jdk.tar.gz")
				.setReleaseType("ga")
				.setVersion("17.0.5")
				.setJavaVersion("17")
				.setJvmImpl("hotspot")
				.setOs("unknown-os-fooos") // unknown OS
				.setArchitecture("unknown-architecture-barch") // unknown architecture
				.setFileType("tar.gz")
				.setImageType("jdk")
				.setUrl("https://example.com/test.tar.gz")
				.setReleaseInfo(Collections.emptyMap())
				.download(download);

		MetadataUtils.saveMetadataFile(testDir.resolve(metadata.metadataFile()), metadata);

		// When
		MetadataUtils.generateComprehensiveIndices(metadataDir, metadataDir, false);

		// Then - verify normalized directories are created
		assertThat(metadataDir.resolve("all.json")).exists();
		Path releaseTypeDir = metadataDir.resolve("ga");
		assertThat(releaseTypeDir.resolve("unknown-os-fooos")).exists().isDirectory();
		Path unknownOsDir = releaseTypeDir.resolve("unknown-os-fooos");
		Path unknownArchDir = unknownOsDir.resolve("unknown-architecture-barch");
		assertThat(unknownArchDir).exists().isDirectory();
		Path imageTypeDir = unknownArchDir.resolve("jdk");
		Path jvmDir = imageTypeDir.resolve("hotspot");
		assertThat(jvmDir.resolve("unknown-distro-mydistro.json")).exists();
	}

	@Test
	void testGenerateComprehensiveIndicesWithDifferentArchitectures() throws Exception {
		// Given - metadata with different architectures
		Path metadataDir = tempDir.resolve("metadata");
		Path distroDir = metadataDir;
		Path temurinDir = distroDir.resolve("temurin");
		Files.createDirectories(temurinDir);

		DownloadResult download1 = new DownloadResult("md5-1", "sha1-1", "sha256-1", "sha512-1", 100_000_000L);
		JdkMetadata metadata1 = JdkMetadata.create()
				.setDistro("temurin")
				.setFilename("temurin-x64.tar.gz")
				.setReleaseType("ga")
				.setVersion("17.0.5")
				.setJavaVersion("17")
				.setJvmImpl("hotspot")
				.setOs("linux")
				.setArchitecture("x86_64")
				.setFileType("tar.gz")
				.setImageType("jdk")
				.setUrl("https://example.com/x64.tar.gz")
				.setReleaseInfo(Collections.emptyMap())
				.download(download1);

		DownloadResult download2 = new DownloadResult("md5-2", "sha1-2", "sha256-2", "sha512-2", 100_000_001L);
		JdkMetadata metadata2 = JdkMetadata.create()
				.setDistro("temurin")
				.setFilename("temurin-aarch64.tar.gz")
				.setReleaseType("ga")
				.setVersion("17.0.5")
				.setJavaVersion("17")
				.setJvmImpl("hotspot")
				.setOs("linux")
				.setArchitecture("aarch64")
				.setFileType("tar.gz")
				.setImageType("jdk")
				.setUrl("https://example.com/aarch64.tar.gz")
				.setReleaseInfo(Collections.emptyMap())
				.download(download2);

		MetadataUtils.saveMetadataFile(temurinDir.resolve(metadata1.metadataFile()), metadata1);
		MetadataUtils.saveMetadataFile(temurinDir.resolve(metadata2.metadataFile()), metadata2);

		// When
		MetadataUtils.generateComprehensiveIndices(metadataDir, metadataDir, false);

		// Then - verify separate architecture directories
		Path gaLinuxDir = metadataDir.resolve("ga/linux");
		assertThat(gaLinuxDir.resolve("x86_64")).exists().isDirectory();
		assertThat(gaLinuxDir.resolve("aarch64")).exists().isDirectory();

		String x64Content = Files.readString(gaLinuxDir.resolve("x86_64/jdk/hotspot/temurin.json"));
		assertThat(x64Content).contains("temurin-x64.tar.gz");
		assertThat(x64Content).doesNotContain("aarch64");

		String aarch64Content = Files.readString(gaLinuxDir.resolve("aarch64/jdk/hotspot/temurin.json"));
		assertThat(aarch64Content).contains("temurin-aarch64.tar.gz");
		assertThat(aarch64Content).doesNotContain("x86_64");
	}

	@Test
	void testGenerateComprehensiveIndicesWithDifferentImageTypes() throws Exception {
		// Given - metadata with different image types (jdk, jre)
		Path metadataDir = tempDir.resolve("metadata");
		Path distroDir = metadataDir;
		Path temurinDir = distroDir.resolve("temurin");
		Files.createDirectories(temurinDir);

		DownloadResult download1 = new DownloadResult("md5-1", "sha1-1", "sha256-1", "sha512-1", 100_000_000L);
		JdkMetadata metadata1 = JdkMetadata.create()
				.setDistro("temurin")
				.setFilename("temurin-jdk.tar.gz")
				.setReleaseType("ga")
				.setVersion("17.0.5")
				.setJavaVersion("17")
				.setJvmImpl("hotspot")
				.setOs("linux")
				.setArchitecture("x86_64")
				.setFileType("tar.gz")
				.setImageType("jdk")
				.setUrl("https://example.com/jdk.tar.gz")
				.setReleaseInfo(Collections.emptyMap())
				.download(download1);

		DownloadResult download2 = new DownloadResult("md5-2", "sha1-2", "sha256-2", "sha512-2", 100_000_001L);
		JdkMetadata metadata2 = JdkMetadata.create()
				.setDistro("temurin")
				.setFilename("temurin-jre.tar.gz")
				.setReleaseType("ga")
				.setVersion("17.0.5")
				.setJavaVersion("17")
				.setJvmImpl("hotspot")
				.setOs("linux")
				.setArchitecture("x86_64")
				.setFileType("tar.gz")
				.setImageType("jre")
				.setUrl("https://example.com/jre.tar.gz")
				.setReleaseInfo(Collections.emptyMap())
				.download(download2);

		MetadataUtils.saveMetadataFile(temurinDir.resolve(metadata1.metadataFile()), metadata1);
		MetadataUtils.saveMetadataFile(temurinDir.resolve(metadata2.metadataFile()), metadata2);

		// When
		MetadataUtils.generateComprehensiveIndices(metadataDir, metadataDir, false);

		// Then - verify separate image_type directories
		Path gaLinuxArchDir = metadataDir.resolve("ga/linux/x86_64");
		assertThat(gaLinuxArchDir.resolve("jdk")).exists().isDirectory();
		assertThat(gaLinuxArchDir.resolve("jre")).exists().isDirectory();

		String jdkContent = Files.readString(gaLinuxArchDir.resolve("jdk/hotspot/temurin.json"));
		assertThat(jdkContent).contains("temurin-jdk.tar.gz");
		assertThat(jdkContent).doesNotContain("jre");

		String jreContent = Files.readString(gaLinuxArchDir.resolve("jre/hotspot/temurin.json"));
		assertThat(jreContent).contains("temurin-jre.tar.gz");
		assertThat(jreContent).doesNotContain("\"jdk\"");
	}

	@Test
	void testGenerateComprehensiveIndicesWithDifferentJvmImplementations() throws Exception {
		// Given - metadata with different JVM implementations
		Path metadataDir = tempDir.resolve("metadata");
		Path distroDir = metadataDir;
		Path temurinDir = distroDir.resolve("temurin");
		Files.createDirectories(temurinDir);

		DownloadResult download1 = new DownloadResult("md5-1", "sha1-1", "sha256-1", "sha512-1", 100_000_000L);
		JdkMetadata metadata1 = JdkMetadata.create()
				.setDistro("temurin")
				.setFilename("temurin-hotspot.tar.gz")
				.setReleaseType("ga")
				.setVersion("17.0.5")
				.setJavaVersion("17")
				.setJvmImpl("hotspot")
				.setOs("linux")
				.setArchitecture("x86_64")
				.setFileType("tar.gz")
				.setImageType("jdk")
				.setUrl("https://example.com/hotspot.tar.gz")
				.setReleaseInfo(Collections.emptyMap())
				.download(download1);

		DownloadResult download2 = new DownloadResult("md5-2", "sha1-2", "sha256-2", "sha512-2", 100_000_001L);
		JdkMetadata metadata2 = JdkMetadata.create()
				.setDistro("temurin")
				.setFilename("temurin-openj9.tar.gz")
				.setReleaseType("ga")
				.setVersion("17.0.5")
				.setJavaVersion("17")
				.setJvmImpl("openj9")
				.setOs("linux")
				.setArchitecture("x86_64")
				.setFileType("tar.gz")
				.setImageType("jdk")
				.setUrl("https://example.com/openj9.tar.gz")
				.setReleaseInfo(Collections.emptyMap())
				.download(download2);

		MetadataUtils.saveMetadataFile(temurinDir.resolve(metadata1.metadataFile()), metadata1);
		MetadataUtils.saveMetadataFile(temurinDir.resolve(metadata2.metadataFile()), metadata2);

		// When
		MetadataUtils.generateComprehensiveIndices(metadataDir, metadataDir, false);

		// Then - verify separate jvm_impl directories
		Path gaLinuxArchImageDir = metadataDir.resolve("ga/linux/x86_64/jdk");
		assertThat(gaLinuxArchImageDir.resolve("hotspot")).exists().isDirectory();
		assertThat(gaLinuxArchImageDir.resolve("openj9")).exists().isDirectory();

		String hotspotContent = Files.readString(gaLinuxArchImageDir.resolve("hotspot/temurin.json"));
		assertThat(hotspotContent).contains("temurin-hotspot.tar.gz");
		assertThat(hotspotContent).doesNotContain("openj9");

		String openj9Content = Files.readString(gaLinuxArchImageDir.resolve("openj9/temurin.json"));
		assertThat(openj9Content).contains("temurin-openj9.tar.gz");
		assertThat(openj9Content).doesNotContain("hotspot");
	}

	@Test
	void testGenerateLatestJsonFromDirectory() throws Exception {
		// Given - multiple metadata files with different versions
		Path distroDir = tempDir.resolve("temurin");
		Files.createDirectories(distroDir);

		DownloadResult download = new DownloadResult("md5", "sha1", "sha256", "sha512", 100_000_000L);

		// Java 8 versions
		JdkMetadata metadata8_1 = JdkMetadata.create()
				.setDistro("temurin")
				.setFilename("temurin-jdk-8u302.tar.gz")
				.setReleaseType("ga")
				.setVersion("8u302")
				.setJavaVersion("8")
				.setJvmImpl("hotspot")
				.setOs("linux")
				.setArchitecture("x86_64")
				.setFileType("tar.gz")
				.setImageType("jdk")
				.setUrl("https://example.com/8u302.tar.gz")
				.setReleaseInfo(Collections.emptyMap())
				.download(download);

		JdkMetadata metadata8_2 = JdkMetadata.create()
				.setDistro("temurin")
				.setFilename("temurin-jdk-8u322.tar.gz")
				.setReleaseType("ga")
				.setVersion("8u322")
				.setJavaVersion("8")
				.setJvmImpl("hotspot")
				.setOs("linux")
				.setArchitecture("x86_64")
				.setFileType("tar.gz")
				.setImageType("jdk")
				.setUrl("https://example.com/8u322.tar.gz")
				.setReleaseInfo(Collections.emptyMap())
				.download(download);

		// Java 11 versions
		JdkMetadata metadata11_1 = JdkMetadata.create()
				.setDistro("temurin")
				.setFilename("temurin-jdk-11.0.15.tar.gz")
				.setReleaseType("ga")
				.setVersion("11.0.15")
				.setJavaVersion("11")
				.setJvmImpl("hotspot")
				.setOs("linux")
				.setArchitecture("x86_64")
				.setFileType("tar.gz")
				.setImageType("jdk")
				.setUrl("https://example.com/11.0.15.tar.gz")
				.setReleaseInfo(Collections.emptyMap())
				.download(download);

		JdkMetadata metadata11_2 = JdkMetadata.create()
				.setDistro("temurin")
				.setFilename("temurin-jdk-11.0.18.tar.gz")
				.setReleaseType("ga")
				.setVersion("11.0.18")
				.setJavaVersion("11")
				.setJvmImpl("hotspot")
				.setOs("linux")
				.setArchitecture("x86_64")
				.setFileType("tar.gz")
				.setImageType("jdk")
				.setUrl("https://example.com/11.0.18.tar.gz")
				.setReleaseInfo(Collections.emptyMap())
				.download(download);

		// Java 17 versions
		JdkMetadata metadata17_1 = JdkMetadata.create()
				.setDistro("temurin")
				.setFilename("temurin-jdk-17.0.5.tar.gz")
				.setReleaseType("ga")
				.setVersion("17.0.5")
				.setJavaVersion("17")
				.setJvmImpl("hotspot")
				.setOs("linux")
				.setArchitecture("x86_64")
				.setFileType("tar.gz")
				.setImageType("jdk")
				.setUrl("https://example.com/17.0.5.tar.gz")
				.setReleaseInfo(Collections.emptyMap())
				.download(download);

		JdkMetadata metadata17_2 = JdkMetadata.create()
				.setDistro("temurin")
				.setFilename("temurin-jdk-17.0.8.tar.gz")
				.setReleaseType("ga")
				.setVersion("17.0.8")
				.setJavaVersion("17")
				.setJvmImpl("hotspot")
				.setOs("linux")
				.setArchitecture("x86_64")
				.setFileType("tar.gz")
				.setImageType("jdk")
				.setUrl("https://example.com/17.0.8.tar.gz")
				.setReleaseInfo(Collections.emptyMap())
				.download(download);

		// Different group - Windows
		JdkMetadata metadata17_windows = JdkMetadata.create()
				.setDistro("temurin")
				.setFilename("temurin-jdk-17.0.5-windows.zip")
				.setReleaseType("ga")
				.setVersion("17.0.5")
				.setJavaVersion("17")
				.setJvmImpl("hotspot")
				.setOs("windows")
				.setArchitecture("x86_64")
				.setFileType("zip")
				.setImageType("jdk")
				.setUrl("https://example.com/17.0.5-win.zip")
				.setReleaseInfo(Collections.emptyMap())
				.download(download);

		MetadataUtils.saveMetadataFile(distroDir.resolve(metadata8_1.metadataFile()), metadata8_1);
		MetadataUtils.saveMetadataFile(distroDir.resolve(metadata8_2.metadataFile()), metadata8_2);
		MetadataUtils.saveMetadataFile(distroDir.resolve(metadata11_1.metadataFile()), metadata11_1);
		MetadataUtils.saveMetadataFile(distroDir.resolve(metadata11_2.metadataFile()), metadata11_2);
		MetadataUtils.saveMetadataFile(distroDir.resolve(metadata17_1.metadataFile()), metadata17_1);
		MetadataUtils.saveMetadataFile(distroDir.resolve(metadata17_2.metadataFile()), metadata17_2);
		MetadataUtils.saveMetadataFile(distroDir.resolve(metadata17_windows.metadataFile()), metadata17_windows);

		// When
		MetadataUtils.generateAllJsonFromDirectory(distroDir, distroDir, false);

		// Then - verify all.json exists with all entries
		Path allJson = distroDir.resolve("all.json");
		assertThat(allJson).exists();
		String allContent = Files.readString(allJson);
		assertThat(allContent).contains("8u302", "8u322", "11.0.15", "11.0.18", "17.0.5", "17.0.8");

		// Verify latest.json exists with only latest per major version per group
		Path latestJson = distroDir.resolve("latest.json");
		assertThat(latestJson).exists();
		String latestContent = Files.readString(latestJson);

		// Should contain latest of Java 8 (8u322)
		assertThat(latestContent).contains("8u322");
		assertThat(latestContent).doesNotContain("8u302");

		// Should contain latest of Java 11 (11.0.18)
		assertThat(latestContent).contains("11.0.18");
		assertThat(latestContent).doesNotContain("11.0.15");

		// Should contain latest of Java 17 for Linux (17.0.8)
		assertThat(latestContent).contains("17.0.8");

		// Should contain Java 17 for Windows (17.0.5) as it's in a different group
		assertThat(latestContent).contains("17.0.5-windows");

		// Parse and verify structure
		JdkMetadata[] latestArray = parseMetadatas(latestContent);

		// Should have 4 entries: latest of 8, 11, 17 (linux), 17 (windows)
		assertThat(latestArray).hasSize(4);
	}

	@Test
	void testGenerateLatestJsonWithComprehensiveIndices() throws Exception {
		// Given - metadata directory with multiple versions
		Path metadataDir = tempDir.resolve("metadata");
		Path distroDir = metadataDir.resolve("temurin");
		Files.createDirectories(distroDir);

		DownloadResult download = new DownloadResult("md5", "sha1", "sha256", "sha512", 100_000_000L);

		// Multiple versions of Java 17
		JdkMetadata metadata17_1 = JdkMetadata.create()
				.setDistro("temurin")
				.setVendor("temurin")
				.setFilename("temurin-jdk-17.0.5.tar.gz")
				.setReleaseType("ga")
				.setVersion("17.0.5")
				.setJavaVersion("17")
				.setJvmImpl("hotspot")
				.setOs("linux")
				.setArchitecture("x86_64")
				.setFileType("tar.gz")
				.setImageType("jdk")
				.setUrl("https://example.com/17.0.5.tar.gz")
				.setReleaseInfo(Collections.emptyMap())
				.download(download);

		JdkMetadata metadata17_2 = JdkMetadata.create()
				.setDistro("temurin")
				.setVendor("temurin")
				.setFilename("temurin-jdk-17.0.8.tar.gz")
				.setReleaseType("ga")
				.setVersion("17.0.8")
				.setJavaVersion("17")
				.setJvmImpl("hotspot")
				.setOs("linux")
				.setArchitecture("x86_64")
				.setFileType("tar.gz")
				.setImageType("jdk")
				.setUrl("https://example.com/17.0.8.tar.gz")
				.setReleaseInfo(Collections.emptyMap())
				.download(download);

		MetadataUtils.saveMetadataFile(distroDir.resolve(metadata17_1.metadataFile()), metadata17_1);
		MetadataUtils.saveMetadataFile(distroDir.resolve(metadata17_2.metadataFile()), metadata17_2);

		// When
		MetadataUtils.generateComprehensiveIndices(metadataDir, metadataDir, false);

		// Then - verify latest.json exists
		Path latestJson = metadataDir.resolve("latest.json");
		assertThat(latestJson).exists();

		String latestContent = Files.readString(latestJson);
		// Should contain only the latest version (17.0.8)
		assertThat(latestContent).contains("17.0.8");
		assertThat(latestContent).doesNotContain("17.0.5");

		// Parse and verify structure
		JdkMetadata[] latestArray = parseMetadatas(latestContent);

		// Should have only 1 entry (latest of 17)
		assertThat(latestArray).hasSize(1);
		assertThat(latestArray[0].getVersion()).isEqualTo("17.0.8");
	}

	@Test
	void testFilterLatestVersionsPreferencesGaOverEa() throws Exception {
		// Given - metadata with both GA and EA versions
		Path distroDir = tempDir.resolve("temurin");
		Files.createDirectories(distroDir);

		DownloadResult download = new DownloadResult("md5", "sha1", "sha256", "sha512", 100_000_000L);

		// Java 17 - both GA and EA versions with EA being newer
		JdkMetadata metadata17_ga = JdkMetadata.create()
				.setDistro("temurin")
				.setFilename("temurin-jdk-17.0.8-ga.tar.gz")
				.setReleaseType("ga")
				.setVersion("17.0.8")
				.setJavaVersion("17")
				.setJvmImpl("hotspot")
				.setOs("linux")
				.setArchitecture("x86_64")
				.setFileType("tar.gz")
				.setImageType("jdk")
				.setUrl("https://example.com/17.0.8-ga.tar.gz")
				.setReleaseInfo(Collections.emptyMap())
				.download(download);

		JdkMetadata metadata17_ea = JdkMetadata.create()
				.setDistro("temurin")
				.setFilename("temurin-jdk-17.0.10-ea.tar.gz")
				.setReleaseType("ea")
				.setVersion("17.0.10")
				.setJavaVersion("17")
				.setJvmImpl("hotspot")
				.setOs("linux")
				.setArchitecture("x86_64")
				.setFileType("tar.gz")
				.setImageType("jdk")
				.setUrl("https://example.com/17.0.10-ea.tar.gz")
				.setReleaseInfo(Collections.emptyMap())
				.download(download);

		// Java 18 - only EA version
		JdkMetadata metadata18_ea = JdkMetadata.create()
				.setDistro("temurin")
				.setFilename("temurin-jdk-18.0.1-ea.tar.gz")
				.setReleaseType("ea")
				.setVersion("18.0.1")
				.setJavaVersion("18")
				.setJvmImpl("hotspot")
				.setOs("linux")
				.setArchitecture("x86_64")
				.setFileType("tar.gz")
				.setImageType("jdk")
				.setUrl("https://example.com/18.0.1-ea.tar.gz")
				.setReleaseInfo(Collections.emptyMap())
				.download(download);

		MetadataUtils.saveMetadataFile(distroDir.resolve(metadata17_ga.metadataFile()), metadata17_ga);
		MetadataUtils.saveMetadataFile(distroDir.resolve(metadata17_ea.metadataFile()), metadata17_ea);
		MetadataUtils.saveMetadataFile(distroDir.resolve(metadata18_ea.metadataFile()), metadata18_ea);

		// When
		MetadataUtils.generateAllJsonFromDirectory(distroDir, distroDir, false);

		// Then - verify latest.json prefers GA over EA
		Path latestJson = distroDir.resolve("latest.json");
		assertThat(latestJson).exists();
		String latestContent = Files.readString(latestJson);

		// Should contain Java 17 GA (17.0.8) not EA (17.0.10)
		assertThat(latestContent).contains("17.0.8");
		assertThat(latestContent).doesNotContain("17.0.10");

		// Should contain Java 18 EA since no GA exists
		assertThat(latestContent).contains("18.0.1");

		// Parse and verify
		JdkMetadata[] latestArray = parseMetadatas(latestContent);

		// Should have 2 entries: Java 17 GA and Java 18 EA
		assertThat(latestArray).hasSize(2);
		assertThat(latestArray[0].getReleaseType()).isEqualTo("ga");
		assertThat(latestArray[0].getVersion()).isEqualTo("17.0.8");
		assertThat(latestArray[1].getReleaseType()).isEqualTo("ea");
		assertThat(latestArray[1].getVersion()).isEqualTo("18.0.1");
	}

	@Test
	void testFilterLatestVersionsKeepsAllFileTypes() throws Exception {
		// Given - metadata with different file types for same version
		Path distroDir = tempDir.resolve("temurin");
		Files.createDirectories(distroDir);

		DownloadResult download = new DownloadResult("md5", "sha1", "sha256", "sha512", 100_000_000L);

		// Java 17 with multiple file types
		JdkMetadata metadata17_targz = JdkMetadata.create()
				.setDistro("temurin")
				.setFilename("temurin-jdk-17.0.8.tar.gz")
				.setReleaseType("ga")
				.setVersion("17.0.8")
				.setJavaVersion("17")
				.setJvmImpl("hotspot")
				.setOs("linux")
				.setArchitecture("x86_64")
				.setFileType("tar.gz")
				.setImageType("jdk")
				.setUrl("https://example.com/17.0.8.tar.gz")
				.setReleaseInfo(Collections.emptyMap())
				.download(download);

		JdkMetadata metadata17_zip = JdkMetadata.create()
				.setDistro("temurin")
				.setFilename("temurin-jdk-17.0.8.zip")
				.setReleaseType("ga")
				.setVersion("17.0.8")
				.setJavaVersion("17")
				.setJvmImpl("hotspot")
				.setOs("linux")
				.setArchitecture("x86_64")
				.setFileType("zip")
				.setImageType("jdk")
				.setUrl("https://example.com/17.0.8.zip")
				.setReleaseInfo(Collections.emptyMap())
				.download(download);

		JdkMetadata metadata17_deb = JdkMetadata.create()
				.setDistro("temurin")
				.setFilename("temurin-jdk-17.0.8.deb")
				.setReleaseType("ga")
				.setVersion("17.0.8")
				.setJavaVersion("17")
				.setJvmImpl("hotspot")
				.setOs("linux")
				.setArchitecture("x86_64")
				.setFileType("deb")
				.setImageType("jdk")
				.setUrl("https://example.com/17.0.8.deb")
				.setReleaseInfo(Collections.emptyMap())
				.download(download);

		MetadataUtils.saveMetadataFile(distroDir.resolve(metadata17_targz.metadataFile()), metadata17_targz);
		MetadataUtils.saveMetadataFile(distroDir.resolve(metadata17_zip.metadataFile()), metadata17_zip);
		MetadataUtils.saveMetadataFile(distroDir.resolve(metadata17_deb.metadataFile()), metadata17_deb);

		// When
		MetadataUtils.generateAllJsonFromDirectory(distroDir, distroDir, false);

		// Then - verify latest.json contains all file types
		Path latestJson = distroDir.resolve("latest.json");
		assertThat(latestJson).exists();
		String latestContent = Files.readString(latestJson);

		// Should contain all three file types
		assertThat(latestContent).contains(".tar.gz", ".zip", ".deb");

		// Parse and verify
		JdkMetadata[] latestArray = parseMetadatas(latestContent);

		// Should have 3 entries: one for each file type
		assertThat(latestArray).hasSize(3);
		assertThat(latestArray).extracting(JdkMetadata::getFileType).containsExactlyInAnyOrder("tar.gz", "zip", "deb");
	}

	@Test
	void testFilterLatestVersionsIncludesOnlyWinningVersionFileTypes() throws Exception {
		// Given - multiple versions with different file types
		Path distroDir = tempDir.resolve("temurin");
		Files.createDirectories(distroDir);

		DownloadResult download = new DownloadResult("md5", "sha1", "sha256", "sha512", 100_000_000L);

		// Java 17.0.8 GA with tar.gz and zip
		JdkMetadata metadata17_8_targz = JdkMetadata.create()
				.setDistro("temurin")
				.setFilename("temurin-jdk-17.0.8.tar.gz")
				.setReleaseType("ga")
				.setVersion("17.0.8")
				.setJavaVersion("17")
				.setJvmImpl("hotspot")
				.setOs("linux")
				.setArchitecture("x86_64")
				.setFileType("tar.gz")
				.setImageType("jdk")
				.setUrl("https://example.com/17.0.8.tar.gz")
				.setReleaseInfo(Collections.emptyMap())
				.download(download);

		JdkMetadata metadata17_8_zip = JdkMetadata.create()
				.setDistro("temurin")
				.setFilename("temurin-jdk-17.0.8.zip")
				.setReleaseType("ga")
				.setVersion("17.0.8")
				.setJavaVersion("17")
				.setJvmImpl("hotspot")
				.setOs("linux")
				.setArchitecture("x86_64")
				.setFileType("zip")
				.setImageType("jdk")
				.setUrl("https://example.com/17.0.8.zip")
				.setReleaseInfo(Collections.emptyMap())
				.download(download);

		// Java 17.0.10 EA with tar.gz, zip, and deb (newer version but EA)
		JdkMetadata metadata17_10_targz = JdkMetadata.create()
				.setDistro("temurin")
				.setFilename("temurin-jdk-17.0.10-ea.tar.gz")
				.setReleaseType("ea")
				.setVersion("17.0.10")
				.setJavaVersion("17")
				.setJvmImpl("hotspot")
				.setOs("linux")
				.setArchitecture("x86_64")
				.setFileType("tar.gz")
				.setImageType("jdk")
				.setUrl("https://example.com/17.0.10-ea.tar.gz")
				.setReleaseInfo(Collections.emptyMap())
				.download(download);

		JdkMetadata metadata17_10_zip = JdkMetadata.create()
				.setDistro("temurin")
				.setFilename("temurin-jdk-17.0.10-ea.zip")
				.setReleaseType("ea")
				.setVersion("17.0.10")
				.setJavaVersion("17")
				.setJvmImpl("hotspot")
				.setOs("linux")
				.setArchitecture("x86_64")
				.setFileType("zip")
				.setImageType("jdk")
				.setUrl("https://example.com/17.0.10-ea.zip")
				.setReleaseInfo(Collections.emptyMap())
				.download(download);

		JdkMetadata metadata17_10_deb = JdkMetadata.create()
				.setDistro("temurin")
				.setFilename("temurin-jdk-17.0.10-ea.deb")
				.setReleaseType("ea")
				.setVersion("17.0.10")
				.setJavaVersion("17")
				.setJvmImpl("hotspot")
				.setOs("linux")
				.setArchitecture("x86_64")
				.setFileType("deb")
				.setImageType("jdk")
				.setUrl("https://example.com/17.0.10-ea.deb")
				.setReleaseInfo(Collections.emptyMap())
				.download(download);

		MetadataUtils.saveMetadataFile(distroDir.resolve(metadata17_8_targz.metadataFile()), metadata17_8_targz);
		MetadataUtils.saveMetadataFile(distroDir.resolve(metadata17_8_zip.metadataFile()), metadata17_8_zip);
		MetadataUtils.saveMetadataFile(distroDir.resolve(metadata17_10_targz.metadataFile()), metadata17_10_targz);
		MetadataUtils.saveMetadataFile(distroDir.resolve(metadata17_10_zip.metadataFile()), metadata17_10_zip);
		MetadataUtils.saveMetadataFile(distroDir.resolve(metadata17_10_deb.metadataFile()), metadata17_10_deb);

		// When
		MetadataUtils.generateAllJsonFromDirectory(distroDir, distroDir, false);

		// Then - verify latest.json contains only file types from the winning version
		Path latestJson = distroDir.resolve("latest.json");
		assertThat(latestJson).exists();
		String latestContent = Files.readString(latestJson);

		// GA 17.0.8 should win over EA 17.0.10
		// So we should have tar.gz and zip from 17.0.8, but NOT deb from 17.0.10
		assertThat(latestContent).contains("17.0.8");
		assertThat(latestContent).doesNotContain("17.0.10");

		// Parse and verify
		JdkMetadata[] latestArray = parseMetadatas(latestContent);

		// Should have exactly 2 entries: tar.gz and zip from 17.0.8 GA
		assertThat(latestArray).hasSize(2);
		assertThat(latestArray).allMatch(md -> md.getVersion().equals("17.0.8"));
		assertThat(latestArray).allMatch(md -> md.getReleaseType().equals("ga"));
		assertThat(latestArray).extracting(JdkMetadata::getFileType).containsExactlyInAnyOrder("tar.gz", "zip");
		// Should NOT contain deb (which only exists in EA version)
		assertThat(latestArray).extracting(JdkMetadata::getFileType).doesNotContain("deb");
	}

	private JdkMetadata parseMetadata(String json) throws JsonProcessingException, JsonMappingException {
		ObjectMapper objectMapper = new ObjectMapper();
		JdkMetadata metadata = objectMapper.readValue(json, JdkMetadata.class); // Validate JSON
		return metadata;
	}

	private JdkMetadata[] parseMetadatas(String json) throws JsonProcessingException, JsonMappingException {
		ObjectMapper objectMapper = new ObjectMapper();
		JdkMetadata[] metadatas = objectMapper.readValue(json, JdkMetadata[].class); // Validate JSON
		return metadatas;
	}
}
