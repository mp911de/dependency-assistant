/*
 * Copyright 2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package biz.paluch.dap.maven.wrapper;

import java.util.Locale;
import java.util.regex.Matcher;

import biz.paluch.dap.artifact.VersionAware;
import org.jspecify.annotations.Nullable;

/**
 * String-level transforms over Maven wrapper URLs.
 *
 * <p>All methods are pure text rewrites that operate on the Maven coordinate
 * shape captured by {@link MavenWrapperUtils#MAVEN_ARTIFACT_PATTERN}. URLs that
 * do not match the pattern are returned unchanged where a transform depends on
 * the match; non-coordinate transforms (credentials, scheme) only inspect the
 * URL prefix.
 *
 * @author Mark Paluch
 */
class MavenWrapperUrlRewriter {

	private static final String MAVEN_CENTRAL_BASE = "https://repo1.maven.org/maven2/";

	private MavenWrapperUrlRewriter() {
	}

	/**
	 * Remove the {@code user[:password]@} segment from the authority of the given
	 * URL.
	 * @param url the URL to rewrite; must not be {@literal null}.
	 * @return the URL without credentials, or the input when no credentials are
	 * present.
	 */
	static String stripCredentials(String url) {

		int authorityStart = MavenWrapperUrlAnalyzer.authorityStart(url);
		if (authorityStart < 0) {
			return url;
		}

		int authorityEnd = MavenWrapperUrlAnalyzer.authorityEnd(url, authorityStart);

		String authority = url.substring(authorityStart, authorityEnd);
		int at = authority.indexOf('@');
		if (at < 0) {
			return url;
		}

		return url.substring(0, authorityStart) + authority.substring(at + 1) + url.substring(authorityEnd);
	}

	/**
	 * Replace a leading {@code http://} scheme with {@code https://}.
	 * @param url the URL to rewrite; must not be {@literal null}.
	 * @return the upgraded URL, or the input when the scheme is not plain HTTP.
	 */
	static String forceHttps(String url) {

		if (url.regionMatches(true, 0, "http://", 0, "http://".length())) {
			return "https://" + url.substring("http://".length());
		}
		return url;
	}

	/**
	 * Rewrite both the path-version and the file-version segments of the URL to the
	 * given version.
	 * @param url the URL to rewrite; must not be {@literal null}.
	 * @param version the canonical version; must not be {@literal null}.
	 * @return the rewritten URL, or the input when the URL does not match the Maven
	 * artifact pattern.
	 */
	static String replaceVersion(String url, String version) {

		Matcher matcher = MavenWrapperUtils.MAVEN_ARTIFACT_PATTERN.matcher(url);
		if (!matcher.find()) {
			return url;
		}

		String start = url.substring(0, matcher.start("version1"));
		String middle = url.substring(matcher.end("version1"), matcher.start("version2"));
		String tail = url.substring(matcher.end("version2"));

		return start + version + middle + version + tail;
	}

	/**
	 * Rewrite both the path-artifact and the file-artifact segments of the URL to
	 * the given canonical artifact id.
	 * @param url the URL to rewrite; must not be {@literal null}.
	 * @param canonicalArtifactId the canonical artifact id; must not be
	 * {@literal null}.
	 * @return the rewritten URL, or the input when the URL does not match the Maven
	 * artifact pattern.
	 */
	static String replaceArtifact(String url, String canonicalArtifactId) {

		Matcher matcher = MavenWrapperUtils.MAVEN_ARTIFACT_PATTERN.matcher(url);
		if (!matcher.find()) {
			return url;
		}

		String start = url.substring(0, matcher.start("artifactId1"));
		String middle = url.substring(matcher.end("artifactId1"), matcher.start("artifactId2"));
		String tail = url.substring(matcher.end("artifactId2"));

		return start + canonicalArtifactId + middle + canonicalArtifactId + tail;
	}

	/**
	 * Replace the last segments of the captured group path with the canonical tail.
	 *
	 * <p>The number of replaced segments equals the segment count of the canonical
	 * group path, so any mirror prefix in the URL is preserved.
	 * @param url the URL to rewrite; must not be {@literal null}.
	 * @param canonicalGroupPathTail the canonical group path (slash-separated);
	 * must not be {@literal null}.
	 * @return the rewritten URL, or the input when the URL does not match the Maven
	 * artifact pattern.
	 */
	static String replaceGroupPath(String url, String canonicalGroupPathTail) {

		Matcher matcher = MavenWrapperUtils.MAVEN_ARTIFACT_PATTERN.matcher(url);
		if (!matcher.find()) {
			return url;
		}

		int groupStart = matcher.start("groupId");
		int groupEnd = matcher.end("groupId");
		String captured = url.substring(groupStart, groupEnd);

		int canonicalSegments = canonicalGroupPathTail.split("/").length;
		int capturedSegments = captured.split("/").length;
		int replaceFrom = groupStart;
		int segmentsToSkip = capturedSegments - canonicalSegments;
		for (int i = 0; i < segmentsToSkip; i++) {
			replaceFrom = url.indexOf('/', replaceFrom) + 1;
		}

		return url.substring(0, replaceFrom) + canonicalGroupPathTail + url.substring(groupEnd);
	}

	/**
	 * Replace the file-name segment of the URL with the canonical file name for the
	 * given wrapper property kind and version.
	 *
	 * <p>For {@link WrapperProperty#DISTRIBUTION} the existing {@code .zip} or
	 * {@code .tar.gz} extension is preserved; any other extension falls back to
	 * {@code .tar.gz}. For {@link WrapperProperty#WRAPPER} the file name always
	 * ends in {@code .jar}.
	 * @param url the URL to rewrite; must not be {@literal null}.
	 * @param kind the wrapper property kind; must not be {@literal null}.
	 * @param version the canonical version; must not be {@literal null}.
	 * @return the rewritten URL.
	 */
	static String replaceFileName(String url, WrapperProperty kind, String version) {

		int lastSlash = url.lastIndexOf('/');
		if (lastSlash < 0) {
			return url;
		}

		String existingFile = url.substring(lastSlash + 1);
		String preservedExtension = detectExtension(existingFile);

		return url.substring(0, lastSlash + 1) + kind.canonicalFileName(version, preservedExtension);
	}

	/**
	 * Return the canonical file-name that would be produced by
	 * {@link #replaceFileName(String, WrapperProperty, String)} applied to the
	 * given URL, without touching the URL.
	 * @param url the URL whose extension informs the suggestion; must not be
	 * {@literal null}.
	 * @param kind the wrapper property kind; must not be {@literal null}.
	 * @param version the canonical version; must not be {@literal null}.
	 * @return the suggested file name.
	 */
	static String replaceFileNameSuggestion(String url, WrapperProperty kind, String version) {

		int lastSlash = url.lastIndexOf('/');
		String preservedExtension = lastSlash < 0 ? null : detectExtension(url.substring(lastSlash + 1));
		return kind.canonicalFileName(version, preservedExtension);
	}

	private static @Nullable String detectExtension(String fileName) {

		String lower = fileName.toLowerCase(Locale.ROOT);
		if (lower.endsWith(".tar.gz")) {
			return "tar.gz";
		}
		if (lower.endsWith(".zip")) {
			return "zip";
		}
		if (lower.endsWith(".jar")) {
			return "jar";
		}
		return null;
	}

	/**
	 * Build the canonical URL for the given wrapper property kind and version.
	 * @param kind the wrapper property kind; must not be {@literal null}.
	 * @param version the canonical version; must not be {@literal null}.
	 * @return the canonical URL.
	 */
	static String canonicalUrl(WrapperProperty kind, VersionAware version) {
		return canonicalUrl(kind, version.getVersion().toString());
	}

	/**
	 * Build the canonical URL for the given wrapper property kind and version.
	 * @param kind the wrapper property kind; must not be {@literal null}.
	 * @param version the canonical version; must not be {@literal null}.
	 * @return the canonical URL.
	 */
	static String canonicalUrl(WrapperProperty kind, String version) {

		return MAVEN_CENTRAL_BASE + kind.canonicalGroupPath() + "/" + kind.canonicalArtifactId()
				+ "/" + version + "/" + kind.canonicalFileName(version, null);
	}

}
