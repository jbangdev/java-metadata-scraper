package dev.jbang.jdkdb;

import static org.assertj.core.api.Assertions.assertThat;

import dev.jbang.jdkdb.model.JdkMetadata;
import java.util.List;
import org.junit.jupiter.api.Test;

class VerifyCommandTest {

	@Test
	void prioritizesUnlistedPresenceThenEaBeforeGa() {
		JdkMetadata listedEa = metadata("listed-ea", null, "ea");
		JdkMetadata blankListedGa = metadata("blank-listed-ga", "", "ga");
		JdkMetadata newerEa = metadata("newer-ea", "2025-01-01", "ea");
		JdkMetadata oldGa = metadata("old-ga", "2024-01-01", "ga");
		JdkMetadata listedGa = metadata("listed-ga", null, "ga");
		JdkMetadata oldEa = metadata("old-ea", "2024-01-01", "ea");

		List<JdkMetadata> ordered = VerifyCommand.prioritizeUnlistedEa(
				List.of(listedEa, blankListedGa, newerEa, oldGa, listedGa, oldEa), false);

		assertThat(ordered)
				.extracting(JdkMetadata::getFilename)
				.containsExactly("newer-ea", "old-ea", "old-ga", "listed-ea", "blank-listed-ga", "listed-ga");
	}

	@Test
	void randomizationPreservesPriorityGroups() {
		List<JdkMetadata> ordered = VerifyCommand.prioritizeUnlistedEa(
				List.of(
						metadata("listed-ga", null, "ga"),
						metadata("newer-ea", "2025-01-01", "ea"),
						metadata("old-ga", "2024-01-01", "ga"),
						metadata("old-ea", "2024-01-01", "ea"),
						metadata("listed-ea", null, "ea")),
				true);

		assertThat(ordered.subList(0, 2))
				.extracting(JdkMetadata::getFilename)
				.containsExactlyInAnyOrder("old-ea", "newer-ea");
		assertThat(ordered.subList(2, ordered.size()))
				.extracting(JdkMetadata::getFilename)
				.containsExactly("old-ga", "listed-ea", "listed-ga");
	}

	private static JdkMetadata metadata(String filename, String unlistedSince, String releaseType) {
		return new JdkMetadata()
				.setFilename(filename)
				.setUnlistedSince(unlistedSince)
				.setReleaseType(releaseType);
	}
}
