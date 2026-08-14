package dev.jbang.jdkdb.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import dev.jbang.jdkdb.scraper.DownloadResult;
import dev.jbang.jdkdb.util.MetadataUtils;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Represents JDK metadata for a specific release */
public class JdkMetadata {
	private static final Logger logger = LoggerFactory.getLogger(JdkMetadata.class);

	public enum ReleaseType {
		ga,
		ea
	}

	public enum ImageType {
		jdk,
		jre
	}

	public enum JvmImpl {
		hotspot,
		openj9,
		graalvm
	}

	public enum Os {
		aix,
		linux,
		macosx,
		solaris,
		windows
	}

	public enum Arch {
		x86_64,
		i686,
		aarch64,
		arm32,
		arm32_vfp_hflt,
		ppc32,
		ppc64,
		ppc64le,
		s390,
		s390x,
		sparcv9,
		riscv64,
		mips,
		mipsel,
		mips64,
		mips64el,
		loong64
	}

	public enum FileType {
		apk,
		deb,
		dmg,
		exe,
		msi,
		pkg,
		rpm,
		tar_gz,
		tar_xz,
		zip
	}

	public enum DistroChecksumType {
		md5,
		sha1,
		sha256,
		sha512
	}

	private String architecture;

	private String distro;

	private List<String> features;

	private String filename;

	@JsonProperty("file_type")
	private String fileType;

	@JsonProperty("image_type")
	private String imageType;

	@JsonProperty("java_version")
	private String javaVersion;

	@JsonProperty("jvm_impl")
	private String jvmImpl;

	private String os;

	@JsonProperty("release_type")
	private String releaseType;

	private String url;

	private String vendor;

	private String version;

	@JsonInclude(JsonInclude.Include.NON_NULL)
	private String checksum;

	@JsonProperty("checksum_type")
	@JsonInclude(JsonInclude.Include.NON_NULL)
	private String checksumType;

	@JsonInclude(JsonInclude.Include.NON_NULL)
	private String md5;

	@JsonInclude(JsonInclude.Include.NON_NULL)
	private String sha1;

	@JsonInclude(JsonInclude.Include.NON_NULL)
	private String sha256;

	@JsonInclude(JsonInclude.Include.NON_NULL)
	private String sha512;

	@JsonInclude(JsonInclude.Include.NON_NULL)
	private Long size;

	@JsonProperty("release_info")
	@JsonInclude(JsonInclude.Include.NON_NULL)
	private Map<String, String> releaseInfo;

	@JsonProperty("unlisted_since")
	@JsonInclude(JsonInclude.Include.NON_NULL)
	private String unlistedSince;

	@JsonProperty("missing_since")
	@JsonInclude(JsonInclude.Include.NON_NULL)
	private String missingSince;

	@JsonProperty("last_verified")
	@JsonInclude(JsonInclude.Include.NON_NULL)
	private String lastVerified;

	@JsonIgnore
	private transient Path metadataFile;

	// Constructors
	public JdkMetadata() {
		releaseType = "ga";
		jvmImpl = "hotspot";
		imageType = "jdk";
		features = new ArrayList<>();
	}

	public String getVendor() {
		return vendor;
	}

	public JdkMetadata setVendor(String vendor) {
		this.vendor = vendor;
		return this;
	}

	public String getDistro() {
		return distro;
	}

	public JdkMetadata setDistro(String distro) {
		this.distro = distro;
		return this;
	}

	public String getFilename() {
		return filename;
	}

	public JdkMetadata setFilename(String filename) {
		this.filename = filename;
		return this;
	}

	public String getReleaseType() {
		return releaseType;
	}

	public ReleaseType releaseTypeEnum() {
		return ReleaseType.valueOf(releaseType);
	}

	public JdkMetadata setReleaseType(String releaseType) {
		this.releaseType = releaseType;
		return this;
	}

	public String getVersion() {
		return version;
	}

	public JdkMetadata setVersion(String version) {
		this.version = version;
		return this;
	}

	public String getJavaVersion() {
		return javaVersion;
	}

	public JdkMetadata setJavaVersion(String javaVersion) {
		this.javaVersion = javaVersion;
		return this;
	}

	public String getJvmImpl() {
		return jvmImpl;
	}

	public JdkMetadata.JvmImpl jvmImplEnum() {
		return JdkMetadata.JvmImpl.valueOf(jvmImpl);
	}

	public JdkMetadata setJvmImpl(String jvmImpl) {
		this.jvmImpl = jvmImpl;
		return this;
	}

	public String getOs() {
		return os;
	}

	public JdkMetadata.Os osEnum() {
		return JdkMetadata.Os.valueOf(os);
	}

	public JdkMetadata setOs(String os) {
		this.os = os;
		return this;
	}

	public String getArchitecture() {
		return architecture;
	}

	public JdkMetadata.Arch archEnum() {
		return JdkMetadata.Arch.valueOf(architecture.replace("-", "_"));
	}

	public JdkMetadata setArchitecture(String architecture) {
		this.architecture = architecture;
		return this;
	}

	public String getFileType() {
		return fileType;
	}

	public JdkMetadata.FileType fileTypeEnum() {
		return JdkMetadata.FileType.valueOf(fileType.replace(".", "_"));
	}

	public JdkMetadata setFileType(String fileType) {
		this.fileType = fileType;
		return this;
	}

	public String getImageType() {
		return imageType;
	}

	public JdkMetadata.ImageType imageTypeEnum() {
		return JdkMetadata.ImageType.valueOf(imageType);
	}

	public JdkMetadata setImageType(String imageType) {
		this.imageType = imageType;
		return this;
	}

	public List<String> getFeatures() {
		return features;
	}

	public JdkMetadata setFeatures(List<String> features) {
		this.features = features;
		return this;
	}

	public String getUrl() {
		return url;
	}

	public JdkMetadata setUrl(String url) {
		this.url = url;
		return this;
	}

	public String getChecksum() {
		return checksum;
	}

	public String getChecksumType() {
		return checksumType;
	}

	public String getMd5() {
		return md5;
	}

	public JdkMetadata setMd5(String md5) {
		this.md5 = md5;
		return this;
	}

	public String getSha1() {
		return sha1;
	}

	public JdkMetadata setSha1(String sha1) {
		this.sha1 = sha1;
		return this;
	}

	public String getSha256() {
		return sha256;
	}

	public JdkMetadata setSha256(String sha256) {
		this.sha256 = sha256;
		return this;
	}

	public String getSha512() {
		return sha512;
	}

	public JdkMetadata setSha512(String sha512) {
		this.sha512 = sha512;
		return this;
	}

	public long getSize() {
		return size == null ? 0 : size;
	}

	public JdkMetadata setSize(long size) {
		this.size = size;
		return this;
	}

	public Map<String, String> getReleaseInfo() {
		return releaseInfo;
	}

	public JdkMetadata setReleaseInfo(Map<String, String> releaseInfo) {
		this.releaseInfo = releaseInfo;
		return this;
	}

	public String getUnlistedSince() {
		return unlistedSince;
	}

	public JdkMetadata setUnlistedSince(String unlistedSince) {
		this.unlistedSince = unlistedSince;
		return this;
	}

	public String getMissingSince() {
		return missingSince;
	}

	public JdkMetadata setMissingSince(String missingSince) {
		this.missingSince = missingSince;
		return this;
	}

	public String getLastVerified() {
		return lastVerified;
	}

	public JdkMetadata setLastVerified(String lastVerified) {
		this.lastVerified = lastVerified;
		return this;
	}

	public Path metadataFile() {
		if (metadataFile == null && filename != null) {
			return Path.of(filename + ".json");
		}
		return metadataFile;
	}

	public JdkMetadata metadataFile(Path metadataFile) {
		this.metadataFile = metadataFile;
		return this;
	}

	public JdkMetadata download(DownloadResult download) {
		this.md5 = download.md5();
		this.sha1 = download.sha1();
		this.sha256 = download.sha256();
		this.sha512 = download.sha512();
		this.size = download.size();
		return this;
	}

	/**
	 * Validate metadata fields and return true if valid, false if invalid.
	 * This checks that required fields are present and that enum values
	 * are valid (or properly marked as unknown).
	 */
	@JsonIgnore
	public boolean isValid() {
		if (getFilename() == null) {
			logger.warn("Missing 'filename'");
			return false;
		}
		if (getUrl() == null) {
			logger.warn("Missing 'url'");
			return false;
		}
		if (getVersion() == null
				|| getVersion().trim().isEmpty()
				|| !getVersion().matches("^\\d.*")) {
			logger.warn("Invalid 'version': {}, must start with a digit", getVersion());
			return false;
		}
		if (getJavaVersion() == null
				|| getJavaVersion().trim().isEmpty()
				|| !getJavaVersion().matches("^\\d.*")) {
			logger.warn("Invalid 'java_version': {}, must start with a digit", getJavaVersion());
			return false;
		}
		if (getVendor() == null || !MetadataUtils.getAllVendors().contains(getVendor())) {
			logger.warn("Invalid 'vendor': {}", getVendor());
			return false;
		}
		if (getDistro() == null || !MetadataUtils.getAllDistros().contains(getDistro())) {
			logger.warn("Invalid 'distro': {}", getDistro());
			return false;
		}
		if (!MetadataUtils.isValidEnumOrUnknown(JdkMetadata.Os.class, getOs())) {
			logger.warn("Invalid 'os': {}", getOs());
			return false;
		}
		if (!MetadataUtils.isValidEnum(JdkMetadata.ImageType.class, getImageType())) {
			logger.warn("Invalid 'image_type': {}", getImageType());
			return false;
		}
		if (!MetadataUtils.isValidEnum(JdkMetadata.JvmImpl.class, getJvmImpl())) {
			logger.warn("Invalid 'jvm_impl': {}", getJvmImpl());
			return false;
		}
		if (!MetadataUtils.isValidEnum(JdkMetadata.ReleaseType.class, getReleaseType())) {
			logger.warn("Invalid 'release_type': {}", getReleaseType());
			return false;
		}
		if (!MetadataUtils.isValidEnumOrUnknown(
				JdkMetadata.Arch.class, getArchitecture().replace("-", "_"))) {
			logger.warn("Invalid 'architecture': {}", getArchitecture());
			return false;
		}
		if (!MetadataUtils.isValidEnumOrUnknown(
				JdkMetadata.FileType.class, getFileType().replace(".", "_"))) {
			logger.warn("Invalid 'file_type': {}", getFileType());
			return false;
		}
		if (getReleaseInfo() != null && !MetadataUtils.isValidReleaseInfo(getReleaseInfo())) {
			logger.warn("Invalid 'release_info': {}", getReleaseInfo());
			return false;
		}
		return true;
	}

	/**
	 * Process the include and exclude file type options to create a filter set.
	 *
	 * @param includeFileTypes List of file types to include (null or empty means include all)
	 * @param excludeFileTypes List of file types to exclude (null or empty means exclude none)
	 * @return A set of file types to accept, or null if no filtering should be applied
	 */
	public static Set<FileType> processFileTypeFilter(
			List<FileType> includeFileTypes, List<FileType> excludeFileTypes) {
		if ((includeFileTypes == null || includeFileTypes.isEmpty())
				&& (excludeFileTypes == null || excludeFileTypes.isEmpty())) {
			return null; // No filtering
		}

		Set<FileType> result;
		if (includeFileTypes != null && !includeFileTypes.isEmpty()) {
			// Start with only the included types
			result = EnumSet.copyOf(includeFileTypes);
		} else {
			// Start with all types
			result = EnumSet.allOf(FileType.class);
		}

		// Remove excluded types
		if (excludeFileTypes != null && !excludeFileTypes.isEmpty()) {
			result.removeAll(excludeFileTypes);
		}

		return result.isEmpty() ? null : result;
	}

	@Override
	public boolean equals(Object o) {
		if (this == o) return true;
		if (o == null || getClass() != o.getClass()) return false;
		JdkMetadata that = (JdkMetadata) o;
		return Objects.equals(distro, that.distro)
				&& Objects.equals(filename, that.filename)
				&& Objects.equals(version, that.version);
	}

	@Override
	public int hashCode() {
		return Objects.hash(distro, filename, version);
	}

	public static JdkMetadata create() {
		return new JdkMetadata();
	}
}
