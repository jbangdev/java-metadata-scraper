# jdkdb-scraper - JDK Metadata DB Scraper

A Java-based application for scraping JDK metadata from various distros.

This application is used daily to update the contents of the [jdkdb-data](https://github.com/jbangdev/jdkdb-data) repository which is then used to generate indices in the [jdkdb-index](https://github.com/jbangdev/jdkdb-index) repository that finally powers the [OpenAPI](https://jbangdev.github.io/jdkdb-index/) page.

This project is based on [Joschi's Java Metadata project](https://github.com/joschi/java-metadata) and incorporates ideas from the [Foojay's Disco API project](https://github.com/foojayio/discoapi).

## Features

- **Parallel Execution**: Run multiple distro scrapers concurrently for improved performance
- **Selective Scraping**: Run all scrapers or select specific distros
- **Extensible Architecture**: Easy to add new distro scrapers
- **Archive Extraction**: Automatically extracts release information from JDK archives

## Running using JBang

Running the released version of the scraper application can simply be done with [JBang](jbang.dev):

```bash
# Update: Run all scrapers
jbang scraper@jbangdev/jdkdb-scraper update

# Update: List available scrapers
jbang scraper@jbangdev/jdkdb-scraper update --list

# Update: Run specific scrapers
jbang scraper@jbangdev/jdkdb-scraper update --scrapers microsoft,semeru,temurin

# Index: Generate all.json files for all distros
jbang scraper@jbangdev/jdkdb-scraper index

# Download: Download and compute missing checksums for all distros
jbang scraper@jbangdev/jdkdb-scraper download

# Clean: Remove incomplete metadata files and prune old EA releases (dry-run)
jbang scraper@jbangdev/jdkdb-scraper clean --dry-run

# Clean: Actually remove incomplete files and prune EA releases older than 6 months
jbang scraper@jbangdev/jdkdb-scraper clean --remove-incomplete=all --prune-ea=6m
```

## Usage

The application provides four main commands:

- **`update`** - Scrape JDK metadata from various distros and update metadata files
- **`index`** - Generate aggregated all.json files for distro directories
- **`download`** - Download and compute checksums for metadata files with missing checksums
- **`clean`** - Clean up metadata by removing incomplete files and pruning old EA releases

### GitHub Authentication

Many scrapers fetch data from GitHub APIs, which have rate limits. To avoid hitting these limits, the application supports GitHub authentication through:

1. **Environment Variable**: Set `GITHUB_TOKEN` to your GitHub personal access token
2. **GitHub CLI**: If you have the GitHub CLI (`gh`) installed and authenticated, the token will be automatically obtained from `gh auth token`

```bash
# Option 1: Using environment variable
export GITHUB_TOKEN=your_github_token_here
java -jar build/libs/jdkdb-scraper-1.0.0-SNAPSHOT-standalone.jar update

# Option 2: Using GitHub CLI (automatic if gh is installed and authenticated)
gh auth login
java -jar build/libs/jdkdb-scraper-1.0.0-SNAPSHOT-standalone.jar update
```

The application checks for tokens in this order: environment variable first, then GitHub CLI.

### Typical usage

- You can simply run `update` in the root of the data repository (where the `metadata/` folder is located) and let it do its work. It will scrape all the distro sites, obtain the latest metadata, download the jdk distributions, calculate checksums and update all the indices. Nothing else to be done. But this can take some time.
- You can split the work into two steps:

1. You run `update --no-download` which will do the scraping and will make sure that we have all the latest distributions cataloged. It will write all the metadata but with _missing_ checksums (and release info).
2. You then run the much heavier and time-consuming `download` process that will look for any metadata file that has missing checksums (or release info) and download the archive to do the calculations. You can limit this step to only process a maximum amount of downloads before stopping. And then you can repeat it after a certain wait time to avoid rate limits and such.

The `index` command can be run any time you think the indices might be out of date and it will generate them all from scratch. _NB:_ if you use the second of the two strategies mentioned above then you will have metadata files with missing checksums, in that case you will need to add the option `--allow-incomplete` or they won't show up in the indices!

And finally the `clean` command can be used to get rid of any invalid or orphaned data. If you run it without any flags it will give an overview of everything that could be cleaned up without actually doing any of the cleaning yet.

### Mark unlisted metadata

You can ask `update` to mark metadata files that are no longer listed by upstream sources:

```bash
jbang scraper@jbangdev/jdkdb-scraper update --mark-unlisted
```

When enabled, the scraper adds `unlisted_since` (current date, `YYYY-MM-DD`) to metadata files that were not listed in the current scan.

Safety rules:

- The field is added, never updated
- The field is removed if the metadata appears again in a future scan
- Marking is done per distro.
- A distro is only marked when all scrapers for that distro ran and all of them completed without errors.
- Index files (`all.json`, `latest.json`) are never marked.

## Command Line Options

### Main Command

```bash
Usage: jdkdb-scraper [-hV] [COMMAND]
Scrapes JDK metadata from various distros and generates index files
	-h, --help      Show this help message and exit.
	-V, --version   Print version information and exit.
Commands:
	update    Scrape JDK metadata from various distros and update metadata files
	index     Generate all.json files for distro directories by aggregating
							individual metadata files
	download  Download and compute checksums for metadata files that have missing
							checksum values
	clean     Clean up metadata by removing incomplete files and pruning old EA
							releases
	convert   Convert all json files to latest format
```

### Update Command

```bash
Usage: jdkdb-scraper update [-hlV] [--from-start] [--mark-unlisted]
							[-c=<checksumDir>]
							[--limit-progress=<limitProgress>]
							[--limit-total=<limitTotal>] [-m=<metadataDir>]
							[--max-failures=<maxFailures>] [-p=<pruneDir>]
							[--skip-ea=<skipEa>] [-t=<maxThreads>]
							[--exclude=<excludeFileTypes>[,
							<excludeFileTypes>...]]...
							[--include=<includeFileTypes>[,
							<includeFileTypes>...]]... [-s=<scraperIds>[,
							<scraperIds>...]]...
Scrape JDK metadata from various distros and update metadata files

Options:
-c, --checksum-dir=<checksumDir>
							Directory to store checksum files (default:
							db/checksums)
	--exclude=<excludeFileTypes>[,<excludeFileTypes>...]
							Exclude these file types (e.g., msi,exe). These
							types will not be downloaded.
	--from-start   Ignore existing metadata files and scrape all items from
							the start
-h, --help         Show this help message and exit.
	--include=<includeFileTypes>[,<includeFileTypes>...]
							Include only these file types (e.g., tar_gz,zip). If
							specified, only these types will be downloaded.
-l, --list         List all available scraper IDs and exit
	--limit-progress=<limitProgress>
							Maximum number of metadata items to process per
							scraper before aborting (default: unlimited)
	--limit-total=<limitTotal>
							Maximum total number of downloads to accept before
							stopping (default: unlimited)
-m, --metadata-dir=<metadataDir>
							Directory to store metadata files (default:
							db/metadata)
	--max-failures=<maxFailures>
							Maximum number of allowed failures per scraper
							before aborting that scraper (default: 10)
	--mark-unlisted
							Mark metadata files that were not listed in the
							current full successful distro scan by adding
							unlisted_since
-s, --scrapers=<scraperIds>[,<scraperIds>...]
							Comma-separated list of scraper IDs to run (if not
							specified, all scrapers run)
	--skip-ea=<skipEa>
							Skip early access (EA) releases older than the
							specified duration (e.g., '6m' for 6 months, '1y'
							for 1 year) (default: 6m)
-t, --threads=<maxThreads>
							Maximum number of parallel scraper threads (default:
							number of processors)
-V, --version      Print version information and exit.
```

### Index Command

```bash
Usage: jdkdb-scraper index [-hV] [--allow-incomplete] [-m=<metadataDir>]
							[-x=<indexDir>] [-v=<distroNames>[,
							<distroNames>...]]...
Generate all.json files for distro directories by aggregating individual
metadata files
	--allow-incomplete   Allow incomplete metadata files (missing checksums)
							to be included
-h, --help         Show this help message and exit.
-m, --metadata-dir=<metadataDir>
							Directory containing metadata files (default:
							db/metadata)
-v, --distros=<distroNames>[,<distroNames>...]
							Comma-separated list of distro names to regenerate
							all.json for (if not specified, all distros are
							processed)
-V, --version      Print version information and exit.
-x, --index-dir=<indexDir>
							Directory to write generated index files to
							(default: db/metadata)
```

### Download Command

```bash
Usage: jdkdb-scraper download [-hV] [--randomize] [--stats-only]
							[-c=<checksumDir>]
							[--limit-progress=<limitProgress>]
							[--limit-total=<limitTotal>] [-m=<metadataDir>]
							[-t=<maxThreads>] [--exclude=<excludeFileTypes>[,
							<excludeFileTypes>...]]...
							[--include=<includeFileTypes>[,
							<includeFileTypes>...]]... [-v=<distroNames>[,
							<distroNames>...]]...
Download and compute checksums for metadata files that have missing checksum
values
-c, --checksum-dir=<checksumDir>
							Directory to store checksum files (default: db/checksums)
	--exclude=<excludeFileTypes>[,<excludeFileTypes>...]
							Exclude these file types (e.g., msi,exe). These types will
							not be downloaded.
-h, --help         Show this help message and exit.
	--include=<includeFileTypes>[,<includeFileTypes>...]
							Include only these file types (e.g., tar_gz,zip). If
							specified, only these types will be downloaded.
	--limit-progress=<limitProgress>
							Maximum number of metadata items to process per scraper
							before aborting (default: unlimited)
	--limit-total=<limitTotal>
							Maximum total number of downloads to accept before
							stopping (default: unlimited)
-m, --metadata-dir=<metadataDir>
							Directory containing metadata files (default: db/metadata)
	--randomize    Randomize the order of downloads instead of processing
							files in order
	--stats-only   Skip downloading files and only show statistics (for
							testing/dry-run)
-t, --threads=<maxThreads>
							Maximum number of parallel download threads (default:
							number of processors)
-v, --distros=<distroNames>[,<distroNames>...]
							Comma-separated list of distro names to process (if not
							specified, all distros are processed)
-V, --version      Print version information and exit.
```

### Clean Command

```bash
Usage: jdkdb-scraper clean [-hV] [--dry-run] [--prune-checksums]
							[--remove-invalid] [-c=<checksumDir>]
							[-m=<metadataDir>] [--prune-ea=<pruneEa>]
							[--remove-incomplete=<removeIncomplete>]
Clean up metadata by removing incomplete files and pruning old EA releases
-c, --checksum-dir=<checksumDir>
							Directory containing checksum files (default:
							db/checksums)
	--dry-run      Show statistics without actually deleting files
-h, --help         Show this help message and exit.
-m, --metadata-dir=<metadataDir>
							Directory containing metadata files (default:
							db/metadata)
	--prune-checksums
							Remove orphaned checksum files that don't have a
							matching metadata file
	--prune-ea=<pruneEa>
							Prune EA releases older than specified duration (e.
							g., 30d, 3w, 6m, 1y). Duration format: [number]
							[d|w|m|y]
	--remove-incomplete=<removeIncomplete>
							Remove metadata files with incomplete data.
							Options: checksums (missing checksums),
							release-info (missing release info), all (either
							missing checksums or release info) (default: all)
	--remove-invalid
							Remove metadata files that fail validation
							(MetadataUtils.isValidMetadata)
-V, --version      Print version information and exit.
```

## Development

### Requirements

- Needs Java 21 or higher

### Building

This project uses Gradle for dependency management and building.

```bash
# Build the project
./gradlew spotlessApply build

# This creates two jars:
# - jdkdb-scraper-1.0.0-SNAPSHOT.jar (regular jar)
# - jdkdb-scraper-1.0.0-SNAPSHOT-standalone.jar (fat jar with all dependencies)
```

### Running using the standalone JAR

```bash
# Show available commands
java -jar build/libs/jdkdb-scraper-1.0.0-SNAPSHOT-standalone.jar --help

# Update: Run all scrapers
java -jar build/libs/jdkdb-scraper-1.0.0-SNAPSHOT-standalone.jar update
```

## Architecture

### Core Components

- **Main**: Entry point with Picocli command dispatcher
- **UpdateCommand**: Scrapes JDK metadata from distros and updates files
- **IndexCommand**: Aggregates individual metadata files into all.json files
- **DownloadCommand**: Downloads JDK files to compute missing checksums
- **CleanCommand**: Cleans up incomplete metadata and prunes old EA releases
- **ProgressReporter**: Central reporting thread that receives and logs progress events from all scrapers
- **ScraperConfig**: Configuration record encapsulating metadata directory, checksum directory, logger, etc
- **BaseScraper**: Abstract base class for all scrapers with common functionality (downloading, hashing, metadata saving)
- **GitHubReleaseScraper**: Specialized base class for scrapers that fetch releases from GitHub
- **AdoptiumMarketplaceScraper**: Specialized base class for scrapers using Adoptium Marketplace API
- **JavaNetBaseScraper**: Specialized base class for scrapers that fetch from java.net archives
- **ScraperFactory**: Factory class that uses ServiceLoader to dynamically discover and instantiate scrapers
- **Scraper.Discovery**: Service provider interface for scraper registration via Java ServiceLoader
- **DownloadManager**: Interface for downloading JDK files (with default and no-op implementations)

### Distro Scrapers

The project includes **35 distro scrapers**, supporting all major JDK distributions:

#### Scraper IDs and Distros

| Scraper ID | Distro | Notes |
|------------|--------|-------|
| `adoptopenjdk` | AdoptOpenJDK | Legacy |
| `bisheng` | Bisheng | Huawei |
| `corretto` | Amazon Corretto | |
| `debian` | Debian | |
| `dragonwell` | Alibaba Dragonwell | |
| `gluon-graalvm` | Gluon GraalVM | |
| `graalvm-ce` | GraalVM CE | Early Access |
| `graalvm-community` | GraalVM Community | |
| `graalvm-community-ea` | GraalVM Community | Early Access |
| `graalvm-legacy` | GraalVM | Legacy versions |
| `ibm` | IBM JDK | |
| `java-se-ri` | Java SE RI | Reference Implementation |
| `jetbrains` | JetBrains Runtime | |
| `kona` | Tencent Kona | |
| `liberica` | BellSoft Liberica | |
| `liberica-native` | BellSoft Liberica Native | Native Image Kit |
| `mandrel` | Mandrel | Red Hat's GraalVM |
| `microsoft` | Microsoft | Microsoft Build of OpenJDK |
| `openjdk` | OpenJDK | |
| `openjdk-leyden` | OpenJDK Leyden | Project Leyden |
| `openjdk-loom` | OpenJDK Loom | Project Loom |
| `openjdk-valhalla` | OpenJDK Valhalla | Project Valhalla |
| `openlogic` | OpenLogic | |
| `oracle` | Oracle | |
| `oracle-graalvm` | Oracle GraalVM | |
| `oracle-graalvm-ea` | Oracle GraalVM | Early Access |
| `redhat` | Red Hat | |
| `sapmachine` | SAP SapMachine | |
| `semeru` | IBM Semeru | |
| `semeru-certified` | IBM Semeru Certified | |
| `temurin` | Eclipse Temurin | Eclipse Adoptium |
| `trava-8` | TravaOpenJDK | Java 8 |
| `trava-11` | TravaOpenJDK | Java 11 |
| `zulu` | Azul Zulu | |
| `zulu-prime` | Azul Zulu Prime | |

**Total: 35 scrapers**

Each scraper is registered via Java's ServiceLoader mechanism in `META-INF/services`.

### Adding New Scrapers

1. Create a new class in `src/main/java/dev/jbang/jdkdb/scraper/impl/` extending `BaseScraper`, `GitHubReleaseScraper`, `AdoptiumMarketplaceScraper`, or `JavaNetBaseScraper`
2. Implement required abstract methods
3. Add an inner `Discovery` class implementing `Scraper.Discovery`
4. Register the discovery class in `src/main/resources/META-INF/services/dev.jbang.jdkdb.scraper.Scraper$Discovery`

Example:

```java
package dev.jbang.jdkdb.scraper.impl;

public class NewDistro extends BaseScraper {
	public NewDistro(ScraperConfig config) {
		super(config);
	}

	@Override
	protected List<JdkMetadata> scrape() throws Exception {
		List<JdkMetadata> allMetadata = new ArrayList<>();

		try {
			// Figure out what items to scrape here...
			var allItems = ...;
			for (item in allItems) {
				try {
					JdkMetadata metadata = scrapeItem(item);
					allMetadata.add(metadata);
				} catch (InterruptedProgressException|TooManyFailuresException e) {
					// We don't want these exceptions to mark the item as failed
					throw e;
				} catch (Exception e) {
					// For recoverable problems we call fail():
					fail(filename, e);
				}
			}
		} catch (InterruptedProgressException ex) {
			// When we receive this exception we stop scraping
			// but we return whatever is already processed
		}

		return allMetadata;
	}

	private JdkMetadata scrapeItem(...) throws Exception {
		// Here we scrape a single item
		JdkMetadata metadata = ...;
		// For each successful item created we call:
		saveMetadataFile(metadata);
		success(filename);
		return metadata;
	}

	// ServiceLoader discovery
	public static class Discovery implements Scraper.Discovery {
		@Override
		public String name() {
			return "new-distro";
		}

		@Override
		public String distro() {
			return "New Distro";
		}

		@Override
		public Scraper create(ScraperConfig config) {
			return new NewDistro(config);
		}
	}
}
```

## Output

The scrapers and indexer generate structured output in the `metadata/` directory:

### Metadata Files (`db/metadata`)

1. **Top-level aggregated indexes**:
- `all.json` - All JDK releases across all distros
- `ga.json` - General Availability (stable) releases only
- `ea.json` - Early Access releases only
- `latest.json` - Latest releases per distro
2. **Organized by release type** (`all/`, `ea/`, `ga/`):
- OS-specific files: `linux.json`, `macosx.json`, `windows.json`, `aix.json`, `solaris.json`
- Architecture-specific subdirectories with further breakdowns
3. **Distro-specific metadata** (`<distro-name>/`):
- Individual `.json` files for each JDK release
- `all.json` file combining all releases for that distro

### Checksum Files (`db/checksums/`)

- Stored in distro-specific directories: `db/checksums/<distro-name>/`
- Contains MD5, SHA1, SHA256, and SHA512 checksum files
- Organized to match the corresponding metadata files

## Logging

Logs are written to:

- Console (STDOUT) - Real-time progress
- File (`logs/jdkdb-scraper.log`) - Detailed execution log

The logging configuration can be customized in `src/main/resources/logback.xml`.

## Requirements

- Java 21 or higher
- Gradle 8.x (included via wrapper)

## License

See [LICENSE](./LICENSE) file
