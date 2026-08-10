package dev.jbang.jdkdb;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.jbang.jdkdb.model.JdkMetadata;
import dev.jbang.jdkdb.scraper.Scraper;
import dev.jbang.jdkdb.scraper.ScraperResult;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.slf4j.LoggerFactory;
import picocli.CommandLine;

class UpdateCommandTest {

	private static final ObjectMapper MAPPER = new ObjectMapper();

	@TempDir
	Path tempDir;

	@Test
	void findDistrosEligibleForUnlistedMarkingRequiresAllDistroScrapersAndNoFailures() {
		UpdateCommand command = new UpdateCommand();

		Map<String, Scraper.Discovery> allDiscoveries = new HashMap<>();
		allDiscoveries.put("a-1", discovery("a-1", "distro-a"));
		allDiscoveries.put("a-2", discovery("a-2", "distro-a"));
		allDiscoveries.put("b-1", discovery("b-1", "distro-b"));
		allDiscoveries.put("b-2", discovery("b-2", "distro-b"));
		allDiscoveries.put("c-1", discovery("c-1", "distro-c"));
		allDiscoveries.put("d-1", discovery("d-1", "distro-d"));

		Map<String, Scraper> ranScrapers = new HashMap<>();
		ranScrapers.put("a-1", () -> null);
		ranScrapers.put("a-2", () -> null);
		ranScrapers.put("b-1", () -> null);
		ranScrapers.put("c-1", () -> null);
		ranScrapers.put("d-1", () -> null);

		Map<String, ScraperResult> results = new HashMap<>();
		results.put("a-1", ScraperResult.success(1, 0, 0, List.of(metadataRef("a-1.json"))));
		results.put("a-2", ScraperResult.success(1, 0, 0, List.of(metadataRef("a-2.json"))));
		results.put("b-1", ScraperResult.success(1, 0, 0, List.of(metadataRef("b-1.json"))));
		results.put("c-1", ScraperResult.success(1, 0, 1, List.of(metadataRef("c-1.json"))));
		results.put("d-1", ScraperResult.failure(new RuntimeException("boom")));

		Set<String> eligible = command.findDistrosEligibleForUnlistedTreatment(results, allDiscoveries, ranScrapers);

		assertThat(eligible).containsExactly("distro-a");
	}

	@Test
	void pruneOptionsAreNoLongerAccepted() {
		UpdateCommand command = new UpdateCommand();
		CommandLine commandLine = new CommandLine(command);

		assertThatThrownBy(() -> commandLine.parseArgs("--prune-dir", "db/pruned"))
				.isInstanceOf(CommandLine.ParameterException.class)
				.hasMessageContaining("Unknown option");
		assertThatThrownBy(() -> commandLine.parseArgs("--prune-unlisted"))
				.isInstanceOf(CommandLine.ParameterException.class)
				.hasMessageContaining("Unknown option");
	}

	@Test
	void markUnlistedMetadataAlwaysLogsOverviewEvenWithoutEligibleDistros() {
		UpdateCommand command = new UpdateCommand();
		Logger logger = (Logger) LoggerFactory.getLogger("command");
		ListAppender<ILoggingEvent> listAppender = new ListAppender<>();
		listAppender.start();
		logger.addAppender(listAppender);

		try {
			command.markUnlistedMetadata(new HashMap<>(), new HashMap<>(), new HashMap<>());
		} finally {
			logger.detachAppender(listAppender);
			listAppender.stop();
		}

		assertThat(listAppender.list)
				.extracting(ILoggingEvent::getFormattedMessage)
				.contains("Overview of unlisted metadata files");
	}

	@Test
	void findDistrosEligibleForUnlistedMarkingExcludesEmptySuccessfulResults() {
		UpdateCommand command = new UpdateCommand();

		Map<String, Scraper.Discovery> allDiscoveries = new HashMap<>();
		allDiscoveries.put("x-1", discovery("x-1", "distro-x"));

		Map<String, Scraper> ranScrapers = new HashMap<>();
		ranScrapers.put("x-1", () -> null);

		Map<String, ScraperResult> results = new HashMap<>();
		results.put("x-1", ScraperResult.success(0, 0, 0, List.of()));

		Set<String> eligible = command.findDistrosEligibleForUnlistedTreatment(results, allDiscoveries, ranScrapers);

		assertThat(eligible).isEmpty();
	}

	@Test
	void markUnlistedMetadataForDistroMarksUnlistedAndClearsRelistedFiles() throws Exception {
		UpdateCommand command = new UpdateCommand();
		Path metadataRoot = tempDir.resolve("metadata");
		Path distroDir = metadataRoot.resolve("temurin");
		Files.createDirectories(distroDir);
		setField(command, "metadataDir", metadataRoot);

		Path listedFile = distroDir.resolve("listed.json");
		Path unlistedFile = distroDir.resolve("unlisted.json");
		Path alreadyMarkedFile = distroDir.resolve("already-marked.json");
		Path allJson = distroDir.resolve("all.json");

		Files.writeString(listedFile, "{\n  \"filename\": \"listed\",\n  \"unlisted_since\": \"2024-01-01\"\n}\n");
		Files.writeString(unlistedFile, "{\n  \"filename\": \"unlisted\"\n}\n");
		Files.writeString(
				alreadyMarkedFile, "{\n  \"filename\": \"already-marked\",\n  \"unlisted_since\": \"2024-01-01\"\n}\n");
		Files.writeString(allJson, "[]\n");

		Set<Path> listedPaths = Set.of(listedFile.toAbsolutePath().normalize());
		LocalDate markDate = LocalDate.of(2026, 8, 7);

		UpdateCommand.UnlistedUpdateSummary summary =
				command.markUnlistedMetadataForDistro("temurin", listedPaths, markDate);
		int marked = summary.marked();
		int relisted = summary.relisted();

		assertThat(marked).isEqualTo(1);
		assertThat(relisted).isEqualTo(1);
		assertThat(MAPPER.readTree(listedFile.toFile()).get("unlisted_since")).isNull();
		assertThat(MAPPER.readTree(unlistedFile.toFile()).get("unlisted_since").asText())
				.isEqualTo("2026-08-07");
		assertThat(MAPPER.readTree(alreadyMarkedFile.toFile())
						.get("unlisted_since")
						.asText())
				.isEqualTo("2024-01-01");
	}

	private Scraper.Discovery discovery(String name, String distro) {
		return new Scraper.Discovery() {
			@Override
			public String name() {
				return name;
			}

			@Override
			public String distro() {
				return distro;
			}

			@Override
			public String vendor() {
				return "test-vendor";
			}

			@Override
			public Scraper create(dev.jbang.jdkdb.scraper.ScraperConfig config) {
				return () -> ScraperResult.success(0, 0, 0, List.of());
			}
		};
	}

	private JdkMetadata metadataRef(String fileName) {
		return JdkMetadata.create().metadataFile(Path.of(fileName));
	}

	private void setField(Object target, String fieldName, Object value) throws Exception {
		Field field = target.getClass().getDeclaredField(fieldName);
		field.setAccessible(true);
		field.set(target, value);
	}
}
