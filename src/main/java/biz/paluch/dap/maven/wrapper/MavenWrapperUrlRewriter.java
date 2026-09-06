/*
 * Copyright 2026-present the original author or authors.
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
 * the match. Credential removal inspects only the URL authority.
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
	 * Remove credentials from the URL authority, leaving other components intact.
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
	 * Replace both version occurrences in a Maven artifact URL.
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
	 * Replace both artifact ID occurrences in a Maven artifact URL.
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
	 * Replace the group-path tail while preserving the mirror prefix.
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
	 * Replace the filename, preserving a supported archive extension.
	 * <p>Unsupported extensions use the wrapper property's default.
	 */
	static String replaceFileName(String url, WrapperProperty property, String version) {

		int tailStart = tailStart(url);
		int lastSlash = url.lastIndexOf('/', tailStart);
		if (lastSlash < 0) {
			return url;
		}

		String existingFile = url.substring(lastSlash + 1, tailStart);
		return url.substring(0, lastSlash + 1)
				+ property.canonicalFileName(version, property.getSupportedExtension(existingFile))
				+ url.substring(tailStart);
	}

	/**
	 * Return the filename suggested by {@link #replaceFileName}.
	 */
	static String replaceFileNameSuggestion(String url, WrapperProperty property, String version) {
		return property.canonicalFileName(version, property.getSupportedExtension(lastUrlSegment(url)));
	}

	/**
	 * Return the Maven Central URL with the default extension.
	 */
	static String canonicalUrl(WrapperProperty property, VersionAware version) {
		return canonicalUrl(property, version.getVersion()
				.toString(), property.defaultExtension());
	}

	/**
	 * Return the Maven Central URL with the default extension.
	 */
	static String canonicalUrl(WrapperProperty property, String version) {
		return canonicalUrl(property, version, null);
	}

	/**
	 * Return the canonical Maven Central URL.
	 * @param preservedExtension a supported extension to retain, or {@literal null}
	 * for the default.
	 */
	static String canonicalUrl(WrapperProperty property, String version, @Nullable String preservedExtension) {

		return MAVEN_CENTRAL_BASE + property.canonicalGroupPath() + "/" + property.canonicalArtifactId()
				+ "/" + version + "/" + property.canonicalFileName(version, preservedExtension);
	}

	/**
	 * Return the offset after {@code ://}, or {@code -1} if absent.
	 */
	static int authorityStart(String url) {

		int schemeEnd = url.indexOf(SCHEME_SEPARATOR);
		if (schemeEnd < 0) {
			return -1;
		}
		return schemeEnd + SCHEME_SEPARATOR.length();
	}

	/**
	 * Return the exclusive authority end offset.
	 * @param authorityStart the non-negative offset from {@link #authorityStart}.
	 */
	static int authorityEnd(String url, int authorityStart) {

		int path = url.indexOf('/', authorityStart);
		int tail = tailStart(url, authorityStart);
		return path < 0 ? tail : Math.min(path, tail);
	}

	/**
	 * Return the offset at which the query or fragment tail of the URL starts, or
	 * the string length when the URL has no tail.
	 */
	static int tailStart(String url) {
		return tailStart(url, 0);
	}

	private static int tailStart(String url, int from) {

		int query = url.indexOf('?', from);
		int fragment = url.indexOf('#', from);
		if (query < 0) {
			return fragment < 0 ? url.length() : fragment;
		}
		return fragment < 0 ? query : Math.min(query, fragment);
	}

	static String lastSegments(String groupPath, int count) {

		String[] segments = groupPath.split("/");
		if (segments.length <= count) {
			return groupPath;
		}
		return String.join("/", Arrays.asList(segments).subList(segments.length - count, segments.length));
	}

	/**
	 * Return the final path segment without query or fragment text.
	 */
	static String lastUrlSegment(String url) {

		int tailStart = tailStart(url);
		int lastSlash = url.lastIndexOf('/', tailStart);
		return url.substring(lastSlash < 0 ? 0 : lastSlash + 1, tailStart);
	}
}
