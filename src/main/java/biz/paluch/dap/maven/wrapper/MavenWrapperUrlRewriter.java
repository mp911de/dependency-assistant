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

import java.util.Arrays;
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

	private static final String SCHEME_SEPARATOR = "://";

	static final String INTERPOLATION_TOKEN = "${";

	private MavenWrapperUrlRewriter() {
	}

	/**
	 * Remove the {@code user[:password]@} segment from the authority of the given
	 * URL.
	 * @param url the URL to rewrite.
	 * @return the URL without credentials, or the input when no credentials are
	 * present.
	 */
	static String stripCredentials(String url) {

		int authorityStart = authorityStart(url);
		if (authorityStart < 0) {
			return url;
		}

		int authorityEnd = authorityEnd(url, authorityStart);

		String authority = url.substring(authorityStart, authorityEnd);
		int at = authority.indexOf('@');
		if (at < 0) {
			return url;
		}

		return url.substring(0, authorityStart) + authority.substring(at + 1) + url.substring(authorityEnd);
	}

	/**
	 * Rewrite both the path-version and the file-version segments of the URL to the
	 * given version.
	 * @param url the URL to rewrite.
	 * @param version the canonical version.
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
	 * @param url the URL to rewrite.
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
	 * @param url the URL to rewrite.
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
		int segmentsToSkip = capturedSegments - canonicalSegments;
		if (segmentsToSkip < 0) {
			return url;
		}

		int replaceFrom = groupStart;
		for (int i = 0; i < segmentsToSkip; i++) {
			int nextSlash = url.indexOf('/', replaceFrom);
			if (nextSlash < 0) {
				return url;
			}
			replaceFrom = nextSlash + 1;
		}

		return url.substring(0, replaceFrom) + canonicalGroupPathTail + url.substring(groupEnd);
	}

	/**
	 * Replace the file-name segment of the URL with the canonical file name for the
	 * given wrapper property and version.
	 *
	 * <p>For {@link WrapperProperty#DISTRIBUTION} the existing {@code .zip} or
	 * {@code .tar.gz} extension is preserved; any other extension falls back to
	 * {@code .tar.gz}. For {@link WrapperProperty#WRAPPER} the file name always
	 * ends in {@code .jar}.
	 * @param url the URL to rewrite.
	 * @param property the wrapper property.
	 * @param version the canonical version.
	 * @return the rewritten URL.
	 */
	static String replaceFileName(String url, WrapperProperty property, String version) {

		int lastSlash = url.lastIndexOf('/');
		if (lastSlash < 0) {
			return url;
		}

		String existingFile = url.substring(lastSlash + 1);
		return url.substring(0, lastSlash + 1)
				+ property.canonicalFileName(version, property.getSupportedExtension(existingFile));
	}

	/**
	 * Return the canonical file-name that would be produced by
	 * {@link #replaceFileName(String, WrapperProperty, String)} applied to the
	 * given URL, without touching the URL.
	 * @param url the URL whose extension informs the suggestion; must not be
	 * {@literal null}.
	 * @param property the wrapper property.
	 * @param version the canonical version.
	 * @return the suggested file name.
	 */
	static String replaceFileNameSuggestion(String url, WrapperProperty property, String version) {

		int lastSlash = url.lastIndexOf('/');
		String preservedExtension = lastSlash < 0 ? null : property.getSupportedExtension(url.substring(lastSlash + 1));
		return property.canonicalFileName(version, preservedExtension);
	}

	/**
	 * Build the canonical URL for the given wrapper property and version.
	 * @param property the wrapper property.
	 * @param version the version carrier supplying the canonical version; must not
	 * be {@literal null}.
	 * @return the canonical URL.
	 */
	static String canonicalUrl(WrapperProperty property, VersionAware version) {
		return canonicalUrl(property, version.getVersion()
				.toString(), property.defaultExtension());
	}

	/**
	 * Build the canonical URL for the given wrapper property and version.
	 * @param property the wrapper property.
	 * @param version the canonical version.
	 * @return the canonical URL.
	 */
	static String canonicalUrl(WrapperProperty property, String version) {
		return canonicalUrl(property, version, null);
	}

	/**
	 * Build the canonical URL for the given wrapper property and version.
	 * @param property the wrapper property.
	 * @param version the canonical version.
	 * @param preservedExtension the distribution archive extension to use, can be
	 * {@literal null}.
	 * @return the canonical URL.
	 */
	static String canonicalUrl(WrapperProperty property, String version, @Nullable String preservedExtension) {

		return MAVEN_CENTRAL_BASE + property.canonicalGroupPath() + "/" + property.canonicalArtifactId()
				+ "/" + version + "/" + property.canonicalFileName(version, preservedExtension);
	}

	/**
	 * Return the start offset of the authority segment (the index just after
	 * {@code ://}), or {@literal -1} when the input does not contain a scheme
	 * separator.
	 * @param url the URL to inspect.
	 * @return the authority start offset, or {@literal -1}.
	 */
	static int authorityStart(String url) {

		int schemeEnd = url.indexOf(SCHEME_SEPARATOR);
		if (schemeEnd < 0) {
			return -1;
		}
		return schemeEnd + SCHEME_SEPARATOR.length();
	}

	/**
	 * Return the end offset of the authority segment (the index of the first
	 * {@code /} at or after {@code authorityStart}, or the string length when no
	 * path separator follows).
	 * @param url the URL to inspect.
	 * @param authorityStart the offset returned by {@link #authorityStart(String)};
	 * must be non-negative.
	 * @return the authority end offset.
	 */
	static int authorityEnd(String url, int authorityStart) {

		int end = url.indexOf('/', authorityStart);
		return end < 0 ? url.length() : end;
	}

	static String lastSegments(String groupPath, int count) {

		String[] segments = groupPath.split("/");
		if (segments.length <= count) {
			return groupPath;
		}
		return String.join("/", Arrays.asList(segments).subList(segments.length - count, segments.length));
	}

	static String lastUrlSegment(String url) {

		int lastSlash = url.lastIndexOf('/');
		return lastSlash < 0 ? url : url.substring(lastSlash + 1);
	}
}
