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

package biz.paluch.dap.gradle.wrapper;

import java.net.URI;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import biz.paluch.dap.artifact.VersionAware;

/**
 * String-level transforms over Gradle wrapper distribution URLs.
 *
 * @author Mark Paluch
 */
class GradleWrapperUrlRewriter {

	private static final Pattern FLAVOR_PATTERN = Pattern.compile("-(?<flavor>bin|all)\\.zip$");

	private static final String SCHEME_SEPARATOR = "://";

	static final Pattern ARCHIVE_PATTERN = Pattern.compile(
			"(?<artifact>[\\w.-]+?)-(?<version>\\d[\\w.-]*?)-(?<flavor>bin|all)\\.zip(?=$|[?#])");

	private GradleWrapperUrlRewriter() {
	}

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

	static String replaceVersion(String url, String version) {

		Matcher matcher = GradleWrapperUtils.GRADLE_DISTRIBUTION_PATTERN.matcher(url);
		if (!matcher.find()) {
			return url;
		}

		return url.substring(0, matcher.start("version")) + version + url.substring(matcher.end("version"));
	}

	static String replaceFileName(String url, String version) {

		int lastSlash = url.lastIndexOf('/');
		if (lastSlash < 0) {
			return url;
		}

		int fileStart = lastSlash + 1;
		int fileEnd = tailStart(url, fileStart);
		String existingFileName = url.substring(fileStart, fileEnd);
		String flavor = flavor(existingFileName);

		return url.substring(0, fileStart)
				+ WrapperProperty.DISTRIBUTION.canonicalFileName(version, flavor)
				+ url.substring(fileEnd);
	}

	static String replaceFileNameSuggestion(String url, String version) {

		String existingFileName = lastUrlSegment(url);
		return WrapperProperty.DISTRIBUTION.canonicalFileName(version, flavor(existingFileName));
	}

	static String canonicalUrl(VersionAware version) {
		return canonicalUrl(version.getVersion().toString());
	}

	static String canonicalUrl(String version) {
		return WrapperProperty.DISTRIBUTION.defaultUrl(version);
	}

	private static String flavor(String fileName) {

		Matcher matcher = GradleWrapperUtils.GRADLE_DISTRIBUTION_PATTERN.matcher(fileName);
		if (matcher.find()) {
			return matcher.group("flavor");
		}

		Matcher flavor = FLAVOR_PATTERN.matcher(fileName);
		return flavor.find() ? flavor.group("flavor") : "bin";
	}

	private static int tailStart(String url, int from) {

		int query = url.indexOf('?', from);
		int fragment = url.indexOf('#', from);
		if (query < 0) {
			return fragment < 0 ? url.length() : fragment;
		}
		if (fragment < 0) {
			return query;
		}
		return Math.min(query, fragment);
	}

	static boolean containsCredentials(String decodedValue) {

		try {
			return URI.create(decodedValue).getUserInfo() != null;
		} catch (IllegalArgumentException ex) {
			return false;
		}
	}

	static int authorityStart(String url) {

		int schemeEnd = url.indexOf(SCHEME_SEPARATOR);
		if (schemeEnd < 0) {
			return -1;
		}
		return schemeEnd + SCHEME_SEPARATOR.length();
	}

	static int authorityEnd(String url, int authorityStart) {

		int path = url.indexOf('/', authorityStart);
		int query = url.indexOf('?', authorityStart);
		int fragment = url.indexOf('#', authorityStart);
		return firstPositive(path, query, fragment, url.length());
	}

	static String lastUrlSegment(String url) {

		String withoutTail = url.substring(0, firstPositive(url.indexOf('?'), url.indexOf('#'), -1,
				url.length()));
		int lastSlash = withoutTail.lastIndexOf('/');
		return lastSlash < 0 ? withoutTail : withoutTail.substring(lastSlash + 1);
	}

	private static int firstPositive(int first, int second, int third, int fallback) {

		int result = fallback;
		if (first >= 0) {
			result = Math.min(result, first);
		}
		if (second >= 0) {
			result = Math.min(result, second);
		}
		if (third >= 0) {
			result = Math.min(result, third);
		}
		return result;
	}

}
