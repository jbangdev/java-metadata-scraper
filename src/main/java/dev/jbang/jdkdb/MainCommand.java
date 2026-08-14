package dev.jbang.jdkdb;

import dev.jbang.jdkdb.util.VersionProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import picocli.CommandLine;
import picocli.CommandLine.Command;

/** Main application class with CLI support */
@Command(
		name = "jdkdb-scraper",
		versionProvider = VersionProvider.class,
		description = "Scrapes JDK metadata from various distros and generates index files",
		mixinStandardHelpOptions = true,
		subcommands = {
			UpdateCommand.class,
			IndexCommand.class,
			DownloadCommand.class,
			VerifyCommand.class,
			CleanCommand.class,
			ConvertCommand.class
		})
public class MainCommand {
	public static final Logger logger = LoggerFactory.getLogger("command");
	public static final String GITHUB_TOKEN_ENV = "GITHUB_TOKEN";

	public static void main(String[] args) {
		int exitCode = new CommandLine(new MainCommand()).execute(args);
		System.exit(exitCode);
	}
}
