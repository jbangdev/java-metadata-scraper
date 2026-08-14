package dev.jbang.jdkdb.util;

import static org.assertj.core.api.Assertions.*;

import java.io.IOException;
import java.nio.file.Path;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.api.io.TempDir;

public class ArchiveUtilsTest {

	@TempDir
	Path tempDir;

	@Test
	@EnabledOnOs({OS.MAC})
	@Disabled("Large file download and extraction - only run manually when needed")
	public void testMacOsPkg() throws IOException, InterruptedException {
		Path pkgFile = tempDir.resolve("test-macosx.pkg");
		HttpUtils httpUtils = new HttpUtils();
		httpUtils.downloadFile(
				"https://github.com/ibmruntimes/semeru11-binaries/releases/download/jdk-11.0.29%2B7_openj9-0.56.0/ibm-semeru-open-jdk_aarch64_mac_11.0.29_7_openj9-0.56.0.pkg",
				pkgFile);
		var releaseInfo =
				ArchiveUtils.extractReleaseInfo(pkgFile, pkgFile.getFileName().toString());
		assertThat(releaseInfo).isNotNull();
		assertThat(MetadataUtils.isValidReleaseInfo(releaseInfo)).isTrue();
	}

	@Test
	@Disabled("Large file download and extraction - only run manually when needed")
	public void testMacOsTarGz() throws IOException, InterruptedException {
		Path tarGzFile = tempDir.resolve("test-macosx.tar.gz");
		HttpUtils httpUtils = new HttpUtils();
		httpUtils.downloadFile(
				"https://cache-redirector.jetbrains.com/intellij-jbr/jbr_fd-21-osx-x64-b126.4.tar.gz", tarGzFile);
		var releaseInfo = ArchiveUtils.extractReleaseInfo(
				tarGzFile, tarGzFile.getFileName().toString());
		assertThat(releaseInfo).isNotNull();
		assertThat(MetadataUtils.isValidReleaseInfo(releaseInfo)).isTrue();
	}

	@Test
	@Disabled("Large file download and extraction - only run manually when needed")
	public void testMacOsZip() throws IOException, InterruptedException {
		Path zipFile = tempDir.resolve("test-macosx.zip");
		HttpUtils httpUtils = new HttpUtils();
		httpUtils.downloadFile("https://static.azul.com/zulu/bin/zulu21.28.85-ca-fx-jre21.0.0-macosx_x64.zip", zipFile);
		var releaseInfo =
				ArchiveUtils.extractReleaseInfo(zipFile, zipFile.getFileName().toString());
		assertThat(releaseInfo).isNotNull();
		assertThat(MetadataUtils.isValidReleaseInfo(releaseInfo)).isTrue();
	}

	@Test
	@EnabledOnOs({OS.LINUX, OS.WINDOWS})
	@Disabled("Large file download and extraction - only run manually when needed")
	public void testLinuxWindowsZip() throws IOException, InterruptedException {
		Path zipFile = tempDir.resolve("test-linux-windows.zip");
		HttpUtils httpUtils = new HttpUtils();
		httpUtils.downloadFile("https://static.azul.com/zulu/bin/zulu21.28.85-ca-fx-jre21.0.0-macosx_x64.zip", zipFile);
		var releaseInfo =
				ArchiveUtils.extractReleaseInfo(zipFile, zipFile.getFileName().toString());
		assertThat(releaseInfo).isNotNull();
		assertThat(MetadataUtils.isValidReleaseInfo(releaseInfo)).isTrue();
	}

	@Test
	@EnabledOnOs({OS.LINUX, OS.WINDOWS})
	@Disabled("Large file download and extraction - only run manually when needed")
	public void testLinuxTarGz() throws IOException, InterruptedException {
		Path tarGzFile = tempDir.resolve("test-linux.tar.gz");
		HttpUtils httpUtils = new HttpUtils();
		httpUtils.downloadFile(
				"https://github.com/SAP/SapMachine/releases/download/sapmachine-21.0.13+1/sapmachine-jdk-21.0.13_linux-x64_bin.tar.gz",
				tarGzFile);
		var releaseInfo = ArchiveUtils.extractReleaseInfo(
				tarGzFile, tarGzFile.getFileName().toString());
		assertThat(releaseInfo).isNotNull();
		assertThat(MetadataUtils.isValidReleaseInfo(releaseInfo)).isTrue();
	}

	@Test
	@EnabledOnOs({OS.LINUX})
	@Disabled("Large file download and extraction - only run manually when needed")
	public void testDebPackage() throws IOException, InterruptedException {
		Path debFile = tempDir.resolve("test.deb");
		HttpUtils httpUtils = new HttpUtils();
		httpUtils.downloadFile(
				"https://github.com/bell-sw/Liberica/releases/download/26.0.2%2B13/bellsoft-jdk26.0.2%2B13-linux-amd64.deb",
				debFile);
		var releaseInfo =
				ArchiveUtils.extractReleaseInfo(debFile, debFile.getFileName().toString());
		assertThat(releaseInfo).isNotNull();
		assertThat(MetadataUtils.isValidReleaseInfo(releaseInfo)).isTrue();
	}

	@Test
	@EnabledOnOs({OS.LINUX})
	@Disabled("Large file download and extraction - only run manually when needed")
	public void testRpmPackage() throws IOException, InterruptedException {
		Path rpmFile = tempDir.resolve("test.rpm");
		HttpUtils httpUtils = new HttpUtils();
		httpUtils.downloadFile(
				"https://github.com/bell-sw/Liberica/releases/download/26.0.2%2B13/bellsoft-jdk26.0.2%2B13-linux-amd64.rpm",
				rpmFile);
		var releaseInfo =
				ArchiveUtils.extractReleaseInfo(rpmFile, rpmFile.getFileName().toString());
		assertThat(releaseInfo).isNotNull();
		assertThat(MetadataUtils.isValidReleaseInfo(releaseInfo)).isTrue();
	}

	@Test
	@EnabledOnOs({OS.LINUX})
	@Disabled("Large file download and extraction - only run manually when needed")
	public void testMsiPackage() throws IOException, InterruptedException {
		Path msiFile = tempDir.resolve("test.msi");
		HttpUtils httpUtils = new HttpUtils();
		httpUtils.downloadFile(
				"https://corretto.aws/downloads/resources/8.502.07.1/amazon-corretto-8.502.07.1-windows-x64-jdk.msi",
				msiFile);
		var releaseInfo =
				ArchiveUtils.extractReleaseInfo(msiFile, msiFile.getFileName().toString());
		assertThat(releaseInfo).isNotNull();
		assertThat(MetadataUtils.isValidReleaseInfo(releaseInfo)).isTrue();
	}

	@Test
	@EnabledOnOs({OS.LINUX, OS.WINDOWS})
	@Disabled("Large file download and extraction - only run manually when needed")
	public void testLinuxTarXz() throws IOException, InterruptedException {
		Path tarXzFile = tempDir.resolve("test-linux.tar.xz");
		HttpUtils httpUtils = new HttpUtils();
		httpUtils.downloadFile(
				"https://developers.redhat.com/content-gateway/file/pub/openjdk/adoptium/July_2025/java-17-openjdk-17.0.16.0.8-1.portable.jdk.el.x86_64.tar.xz",
				tarXzFile);
		var releaseInfo = ArchiveUtils.extractReleaseInfo(
				tarXzFile, tarXzFile.getFileName().toString());
		assertThat(releaseInfo).isNotNull();
		assertThat(MetadataUtils.isValidReleaseInfo(releaseInfo)).isTrue();
	}

	@Test
	@EnabledOnOs({OS.LINUX})
	@Disabled("Large file download and extraction - only run manually when needed")
	public void testApkPackage() throws IOException, InterruptedException {
		Path apkFile = tempDir.resolve("test.apk");
		HttpUtils httpUtils = new HttpUtils();
		httpUtils.downloadFile(
				"https://github.com/bell-sw/Liberica/releases/download/8u282+8/bellsoft-jdk8u282+8-linux-aarch64-musl.apk",
				apkFile);
		var releaseInfo =
				ArchiveUtils.extractReleaseInfo(apkFile, apkFile.getFileName().toString());
		assertThat(releaseInfo).isNotNull();
		assertThat(MetadataUtils.isValidReleaseInfo(releaseInfo)).isTrue();
	}
}
